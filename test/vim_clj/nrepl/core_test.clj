(ns vim-clj.nrepl.core-test
  (:require [clojure.test :refer :all]
            [vim-clj.nrepl.core :refer :all]))

(deftest str->conn-map-test
  (are [x y] (= x (str->conn-map y))
    nil                 "9797"
    {:port 9797
     :scope "abc"}      "9797 abc"
    nil                 "localhost:9797"
    {:port 9797
     :host "localhost"
     :scope "abc"}      "localhost:9797 abc"
    nil                 "localhost"))
