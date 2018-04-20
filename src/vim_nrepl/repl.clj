(ns vim-nrepl.repl
  (:require [vim-nrepl.nvim.core :as nvim]
            [vim-nrepl.nvim.methods :as methods]
            [vim-nrepl.nrepl.core :as nrepl]
            [clojure.pprint :refer [pprint]]))

(defn connect-nvim! []
  (nvim/connect-to-port! 7777)
  (nvim/set-is-running 1)
  (nvim/set-channel-var)
  (methods/register-methods!))
