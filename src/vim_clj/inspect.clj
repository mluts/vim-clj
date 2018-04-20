(ns vim-clj.inspect
  (:require [clojure.tools.namespace.parse :as ns-parse]
            [clojure.java.io :as io]))

(defn- reader [clj-file]
  (java.io.PushbackReader. (io/reader clj-file)))

(defn read-ns-name [clj-file]
  (let [[_ ns-name] (ns-parse/read-ns-decl (reader clj-file))]
    (str ns-name)))
