(ns vim-nrepl.nrepl-test
  (:require [clojure.test :refer :all]
            [vim-nrepl.nrepl :refer :all]))

(deftest str->conn-map-test
  (are [x y] (= x (str->conn-map y))
    {:port 9797}        "9797"
    {:port 9797
     :host "localhost"} "localhost:9797"
    nil                 "localhost"))
