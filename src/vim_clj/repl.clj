(ns vim-clj.repl
  (:require [vim-clj.nvim.core :as nvim]
            [vim-clj.nvim.methods :as methods]
            [vim-clj.nrepl.core :as nrepl]
            [clojure.pprint :refer [pprint]]
            [clojure.string :as str]
            [clojure.java.io :as io]))

(defn connect-nvim! []
  (nvim/connect-to-port! 7777)
  (nvim/set-is-running 1)
  (nvim/set-channel-var)
  (methods/register-methods!))
