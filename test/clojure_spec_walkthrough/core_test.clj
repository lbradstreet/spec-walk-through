(ns clojure-spec-walkthrough.core-test
  (:require [clojure.test :refer :all]
            [clojure.spec.test :as stest]
            [clojure-spec-walkthrough.core :refer :all]))

(stest/instrument `hypotenuse)

(deftest spec'd-test
  (is (hypotenuse 33 500.0)))

;(stest/instrumentable-syms 'clojure-spec-walkthrough.core)


