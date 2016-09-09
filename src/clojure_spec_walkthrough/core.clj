(ns clojure-spec-walkthrough.core
  (:require [clojure.spec :as s]
            [clojure.spec.gen :as gen]
            [clojure.spec.test :as stest])
  (:import [java.util Date]))

(s/conform even? 1000)

(s/valid? even? 10)

;; Note that again valid? implicitly converts the predicate function into a spec. 

;; The spec library allows you to leverage all of the functions you already
;; have - there is no special dictionary of predicates. Some more examples:

(s/valid? nil? nil)  ;; true
(s/valid? string? "abc")  ;; true

(s/valid? #(> % 5) 10) ;; true
(s/valid? #(> % 5) 0) ;; false

(s/valid? inst? (Date.))  ;; true

;; Sets can also be used as predicates that match one or more literal values:

(s/valid? #{:club :diamond :heart :spade} :club) ;; true
(s/valid? #{:club :diamond :heart :spade} 42) ;; false

(s/valid? #{42} 42) ;; true

; Registry

; Until now, we’ve been using specs directly. However, spec provides a central
; registry for globally declaring reusable specs. 

; The registry associates a namespaced keyword with a specification. The use of
; namespaces ensures that we can define reusable non-conflicting specs across
; libraries or applications.

; Specs are registered using def. It’s up to you to register the specification
; in a namespace that makes sense  (typically a namespace you control).

(s/def ::date inst?)
(s/def ::suit #{:club :diamond :heart :spade})

; A registered spec identifier can be used in place of a spec definition in the
; operations we’ve seen so far - conform and valid?.

(s/valid? ::date (Date.))
;;=> true
(s/conform ::suit :club)
;;=> :club

; Composing predicates

; The simplest way to compose specs is with and and or. Let’s create a spec
; that combines several predicates into a composite spec with s/and:

(s/def ::big-even (s/and int? even? #(> % 1000)))
(s/valid? ::big-even :foo) ;; false
(s/valid? ::big-even 10) ;; false
(s/valid? ::big-even 100000) ;; true

; We can also use s/or to specify two alternatives:

(s/def ::name-or-id (s/or :name string? :id int?))
(s/valid? ::name-or-id "abc") ;; true
(s/valid? ::name-or-id 100) ;; true
(s/valid? ::name-or-id :foo) ;; false

;(s/explain ::name-or-id :foo)

; This or spec is the first case we’ve seen that involves a choice during
; validity checking. Each choice is annotated with a tag (here, between :name
; and :id) and those tags give the branches names that can be used to
; understand or enrich the data returned from conform and other spec functions.

; When an or is conformed, it returns a vector with the tag name and conformed value:

(s/conform ::name-or-id  "abc")
;;=>  [:name  "abc "]
(s/conform ::name-or-id 100)
;;=>  [:id 100]

; Many predicates that check an instance’s type do not allow nil as a valid value  
; (string?, number?, keyword?, etc). To include nil as a valid value, use the provided function nilable to make a spec:

(s/valid? string? nil)
;;=> false
(s/valid? (s/nilable string?) nil)
;;=> true

; Explain

; explain is another high-level operation in spec that can be used to report  (to *out*) why a value does not conform to a spec. Let’s see what explain says about some non-conforming examples we’ve seen so far.

(s/explain ::suit 42)
;; val: 42 fails spec: ::suit predicate: #{:spade :heart :diamond :club
(s/explain ::big-even 5)
;; val: 5 fails spec: ::big-even predicate: even?
(s/explain ::name-or-id :foo)
;; val: :foo fails spec: ::name-or-id at:  [:name predicate: string?
;; val: :foo fails spec: ::name-or-id at:  [:id predicate: int?

; Let’s examine the output of the final example more closely. First note that there are two errors being reported - spec 
; will evaluate all possible alternatives and report errors on every path. The parts of each error are:

; val - the value in the user’s input that does not match
; spec - the spec that was being evaluated
; at - a path  (a vector of keywords) indicating the location within the spec where the error occurred - the tags in the path correspond to any tagged part in a spec  (the alternatives in an or or alt, the parts of a cat, the keys in a map, etc)
; predicate - the actual predicate that was not satsified by val
; in - the key path through a nested data val to the failing value. In this example, the top-level value is the one that is failing so this is essentially an empty path and is omitted.
; For the first reported error we can see that the value :foo did not satisfy the predicate string? at the path :name in the spec ::name-or-id. The second reported error is similar but fails on the :id path instead. The actual value is a keyword so neither is a match.

; In addition to explain, you can use explain-str to receive the error messages as a string or explain-data to receive the errors as data.

(s/explain-data ::name-or-id :foo)

;;=> #:clojure.spec {
;;     :problems  ({:path  [:name,
;;                 :pred string?,
;;                 :val :foo,
;;                 :via  [:spec.examples.guide/name-or-id,
;;                 :in  []}
;;                {:path  [:id,
;;                 :pred int?,
;;                 :val :foo,
;;                 :via  [:spec.examples.guide/name-or-id,
;;                 :in  []}

; This result also demonstrates the new namespace map literal syntax added in 1.9.0-alpha8. Maps may be prefixed with #: or #
; (for autoresolve) to specify a default namespace for all keys in the map. In this example, this is equivalent to  {:clojure.spec/problems …​}

; Entity Maps

; Clojure programs rely heavily on passing around maps of data. A common
; approach in other libraries is to describe each entity type, combining both
; the keys it contains and the structure of their values. Rather than define
; attribute  (key+value) specifications in the scope of the entity  (the map),
; specs assign meaning to individual attributes, then collect them into maps
; using set semantics  (on the keys). This approach allows us to start
; assigning  (and sharing) semantics at the attribute level across our
; libraries and applications.

; For example, most Ring middleware functions modify the request or response
; map with unqualified keys. However, each middleware could instead use
; namespaced keys with registered semantics for those keys. The keys could then
; be checked for conformance, creating a system with greater opportunities for
; collaboration and consistency.

; Entity maps in spec are defined with keys:

(def email-regex #"^[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\.[a-zA-Z]{2,63}$")

(s/def ::email-type (s/and string? #(re-matches email-regex %)))
(s/def ::acctid int?)
(s/def ::first-name string?)
(s/def ::last-name string?)
(s/def ::email ::email-type)

(s/def ::person (s/keys :req [::first-name ::last-name ::email]
                        :opt [::phone]))

; This registers a ::person spec with the required keys ::first-name,
; ::last-name, and ::email, with optional key ::phone. The map spec never
; specifies the value spec for the attributes, only what attributes are
; required or optional.

; When conformance is checked on a map, it combines two things - checking that
; the required attributes are included, and checking that every registered key
; has a conforming value. We’ll see later where optional attributes can be
; useful. Also note that ALL attributes are checked via keys, not just those
; listed in the :req and :opt keys. Thus a bare  (s/keys) is valid and will check
; all attributes of a map without checking which keys are required or optional.

(s/valid? ::person
  {::first-name "Elon"
   ::last-name "Musk"
   ::email "elon@example.com"})
;;=> true

;; Fails required key check
(s/explain ::person
  {::first-name "Elon"})
;; val: #:my.domain{:first-name "Elon"} fails spec: :my.domain/person
;;  predicate: [(contains? % :my.domain/last-name) (contains? % :my.domain/email)]

;; Fails attribute conformance
(s/explain ::person
  {::first-name "Elon"
   ::last-name "Musk"
   ::email "n/a"})
;; In: [:my.domain/email] val: "n/a" fails spec: :my.domain/email
;;   at: [:my.domain/email] predicate: (re-matches email-regex %)

; Let’s take a moment to examine the explain error output on that final example:

; in - the path within the data to the failing value (here, a key in the person instance)
; val - the failing value, here "n/a"
; spec - the spec that failed, here :my.domain/email
; at - the path in the spec where the failing value is located
; predicate - the predicate that failed, here (re-matches email-regex %)
; Much existing Clojure code does not use maps with namespaced keys and so keys can also specify :req-un and :opt-un for required and optional unqualified keys. These variants specify namespaced keys used to find their specification, but the map only checks for the unqualified version of the keys.

; Let’s consider a person map that uses unqualified keys but checks conformance against the namespaced specs we registered earlier:

(s/def :unq/person
  (s/keys :req-un [::first-name ::last-name ::email]
          :opt-un [::phone]))

(s/conform :unq/person
  {:first-name "Elon"
   :last-name "Musk"
   :email "elon@example.com"})
;;=> {:first-name "Elon", :last-name "Musk", :email "elon@example.com"}

(s/explain :unq/person
  {:first-name "Elon"
   :last-name "Musk"
   :email "n/a"})
;; In: [:email] val: "n/a" fails spec: :my.domain/email at: [:email]
;;   predicate: (re-matches email-regex %)

(s/explain :unq/person
  {:first-name "Elon"})
;; val: {:first-name "Elon"} fails spec: :unq/person
;;   predicate: [(contains? % :last-name) (contains? % :email)]

; Unqualified keys can also be used to validate record attributes:

(defrecord Person [first-name last-name email phone])

(s/explain :unq/person (->Person "Elon" nil nil nil))

;; In: [:last-name] val: nil fails spec: :my.domain/last-name at: [:last-name] predicate: string?
;; In: [:email] val: nil fails spec: :my.domain/email at: [:email] predicate: string?

(s/conform :unq/person
  (->Person "Elon" "Musk" "elon@example.com" nil))
;;=> #my.domain.Person{:first-name "Elon", :last-name "Musk",
;;=>                   :email "elon@example.com", :phone nil}

; One common occurrence in Clojure is the use of "keyword args" where keyword
; keys and values are passed in a sequential data structure as options. Spec
; provides special support for this pattern with the regex op keys*. keys* has
; the same syntax and semantics as keys but can be embedded inside a sequential
; regex structure.

(s/def ::port number?)
(s/def ::host string?)
(s/def ::id keyword?)
(s/def ::server (s/keys* :req [::id ::host] :opt [::port]))
(s/conform ::server [::id :s1 ::host "example.com" ::port 5555])
;;=> {:my.domain/id :s1, :my.domain/host "example.com", :my.domain/port 5555}

; Sometimes it will be convenient to declare entity maps in parts, either because
; there are different sources for requirements on an entity map or because there
; is a common set of keys and variant-specific parts. The s/merge spec can be
; used to combine multiple s/keys specs into a single spec that combines their
; requirements. For example consider two keys specs that define common animal
; attributes and some dog-specific ones. The dog entity itself can be described
; as a merge of those two attribute sets:

(s/def :animal/kind string?)
(s/def :animal/says string?)
(s/def :animal/common (s/keys :req [:animal/kind :animal/says]))
(s/def :dog/tail? boolean?)
(s/def :dog/breed string?)
(s/def :animal/dog (s/merge :animal/common
                            (s/keys :req [:dog/tail? :dog/breed])))
(s/valid? :animal/dog
          {:animal/kind "dog"
           :animal/says "woof"
           :dog/tail? true
           :dog/breed "retriever"})
;;=> true

; Collections

; A few helpers are provided for other special collection cases - coll-of, tuple, and map-of.

; For the special case of a homogenous collection of arbitrary size, you can use coll-of to specify a collection of elements satisfying a predicate.

(s/conform (s/and vector? (s/coll-of keyword?)) [:a :b :c])
;;=> [:a :b :c]
(s/conform (s/coll-of number?) #{5 10 2})
;;=> #{2 5 10}

; Additionally, coll-of can be passed a number of keyword arg options:

; :kind - a predicate or spec that the incoming collection must satisfy, such as vector?
; :count - specifies exact expected count
; :min-count, :max-count - checks that collection has (⇐ min-count count max-count)
; :distinct - checks that all elements are distinct
; :into - one of [], (), {}, or #{} for output conformed value. If :into is not specified, the input collection type will be used.
; Following is an example utilizing some of these options to spec a vector containing three distinct numbers conformed as a set and some of the errors for different kinds of invalid values:

(s/def ::vnum3 (s/coll-of number? :kind vector? :count 3 :distinct true :into #{}))

(s/conform ::vnum3 [1 2 3])
;;=> #{1 2 3}
(s/explain ::vnum3 #{1 2 3})   ;; not a vector
;; val: #{1 3 2} fails spec: ::vnum3 predicate: clojure.core/vector?
(s/explain ::vnum3 [1 1 1])    ;; not distinct
;; val: [1 1 1] fails spec: ::vnum3 predicate: distinct?
(s/explain ::vnum3 [1 2 :a])   ;; not a number
;; In: [2] val: :a fails spec: ::vnum3 predicate: number?

; Both coll-of and map-of will conform all of their elements, which may make
; them unsuitable for large collections. In that case, consider every or for
; maps every-kv.  

; While coll-of is good for homogenous collections of any size,
; another case is a fixed-size positional collection with fields of known type
; at different positions. For that we have tuple.

(s/def ::point (s/tuple double? double? double?))
(s/conform ::point [1.5 2.5 -0.5])
; => [1.5 2.5 -0.5]

; Note that in this case of a "point" structure with x/y/z values we actually had a choice of three possible specs:

; Regular expression - (s/cat :x double? :y double? :z double?)
; Allows for matching nested structure (not needed here)
; Conforms to map with named keys based on the cat tags
; Collection - (s/coll-of double?)
; Designed for arbitrary size homogenous collections
; Conforms to a vector of the values
; Tuple - (s/tuple double? double? double?)
; Designed for fixed size with known positional "fields"
; Conforms to a vector of the values
; In this example, coll-of will match other (invalid) values as well (like [1.0] or [1.0 2.0 3.0 4.0]), so it is not a suitable choice - we want fixed fields. The choice between a regular expression and tuple here is to some degree a matter of taste, possibly informed by whether you expect either the tagged return values or error output to be better with one or the other.

; In addition to the support for information maps via keys, spec also provides map-of for maps with homogenous key and value predicates.

(s/def ::scores (s/map-of string? int?))
(s/conform ::scores {"Sally" 1000, "Joe" "eisntar"})

;(s/explain-data ::scores {"Sally" 1000, "Joe" "eisntar"})

;=> {"Sally" 1000, "Joe" 500}

; By default map-of will validate but not conform keys because conformed keys
; might create key duplicates that would cause entries in the map to be
; overridden. If conformed keys are desired, pass the option `:conform-keys
; true'.

; You can also use the various count-related options on map-of that you have with coll-of.

; Sampling Generators

; The gen function can be used to obtain the generator for any spec.

; Once you have obtained a generator with gen, there are several ways to use
; it. You can generate a single sample value with generate or a series of
; samples with sample. Let’s see some basic examples:

(gen/generate (s/gen int?))
;;=> -959
(gen/generate (s/gen nil?))
;;=> nil
(gen/sample (s/gen string?))
;;=> ("" "" "" "" "8" "W" "" "G74SmCm" "K9sL9" "82vC")
(gen/sample (s/gen #{:club :diamond :heart :spade}))
;;=> (:heart :diamond :heart :heart :heart :diamond :spade :spade :spade :club)

(gen/sample (s/gen (s/cat :k keyword? :ns (s/+ number?))))

;;=> ((:D -2.0)
;;=>  (:q4/c 0.75 -1)
;;=>  (:*!3/? 0)
;;=>  (:+k_?.p*K.*o!d/*V -3)
;;=>  (:i -1 -1 0.5 -0.5 -4)
;;=>  (:?!/! 0.515625 -15 -8 0.5 0 0.75)
;;=>  (:vv_z2.A??!377.+z1*gR.D9+G.l9+.t9/L34p -1.4375 -29 0.75 -1.25)
;;=>  (:-.!pm8bS_+.Z2qB5cd.p.JI0?_2m.S8l.a_Xtu/+OM_34* -2.3125)
;;=>  (:Ci 6.0 -30 -3 1.0)
;;=>  (:s?cw*8.t+G.OS.xh_z2!.cF-b!PAQ_.E98H4_4lSo/?_m0T*7i 4.4375 -3.5 6.0 108 0.33203125 2 8 -0.517578125 -4))

;;;;;;;;;;
; Custom Generators

; Building your own generator gives you the freedom to be either narrower and/or
; be more explicit about what values you want to generate. Alternately, custom
; generators can be used in cases where conformant values can be generated more
; efficiently than using a base predicate plus filtering. Spec does not trust
; custom generators and any values they produce will also be checked by their
; associated spec to guarantee they pass conformance.

; There are three ways to build up custom generators - in decreasing order of preference:

; Let spec create a generator based on a predicate/spec

; Create your own generator from the tools in clojure.spec.gen

; Use test.check or other test.check compatible libraries (like test.chuck)

; The last option requires a runtime dependency on test.check so the first two
; options are strongly preferred over using test.check directly.  First consider
; a spec with a predicate to specify keywords from a particular namespace:

(s/def ::kws (s/and keyword? #(= (namespace %) "my.domain")))

(s/valid? ::kws :my.domain/name) ;; true

(gen/sample (s/gen ::kws)) ;; unlikely we'll generate useful keywords this way

; The simplest way to start generating values for this spec is to have spec
; create a generator from a fixed set of options. A set is a valid predicate
; spec so we can create one and ask for it’s generator:

(def kw-gen (s/gen #{:my.domain/name :my.domain/occupation :my.domain/id}))

(gen/sample kw-gen 5)

;;=> (:my.domain/occupation :my.domain/occupation :my.domain/name :my.domain/id :my.domain/name)
; To redefine our spec using this custom generator, use with-gen which takes a
; spec and a replacement generator:

(s/def ::kws (s/with-gen (s/and keyword? #(= (namespace %) "my.domain"))
               #(s/gen #{:my.domain/name :my.domain/occupation :my.domain/id})))

(s/valid? ::kws :my.domain/name)  ;; true
(gen/sample (s/gen ::kws))

;;=> (:my.domain/occupation :my.domain/occupation :my.domain/name  ...)
; Note that with-gen (and other places that take a custom generator) take a no-arg function that returns the generator, allowing it to be lazily realized.

; One downside to this approach is we are missing what property testing is really good at: automatically generating data across a wide search space to find unexpected problems.

; The clojure.spec.gen namespace has a number of functions for generator "primitives" as well as "combinators" for combining them into more complicated generators.

; In this case we want our keyword to have open names but fixed namespaces. There
; are many ways to accomplish this but one of the simplest is to use fmap to
; build up a keyword based on generated strings:

(def kw-gen-2 (gen/fmap #(keyword "my.domain" %) (gen/string-alphanumeric)))
(gen/sample kw-gen-2 5)

;;;;;;;;;;;;;;;;
;; f/def checking

(defn hypotenuse
  "returns hypotenuse"
  [a b]
  (Math/sqrt (+ (* (bigint a) (bigint a)) (* (bigint b) (bigint b)))))

(s/fdef hypotenuse
        :args (s/cat :a (s/and int? pos?) :b (s/and int? pos?))
        :ret double?
        :fn (s/and #(> (:ret %) (-> % :args :a))
                   #(> (:ret %) (-> % :args :b))))

(stest/check `hypotenuse)

(stest/instrument 'hypotenuse)

;(gen/generate (s/gen (s/and int? even?)))
;(gen/sample (s/gen ::scores))

;(hypotenuse 3 4)
