(ns vim-clj.core
  (:require [vim-clj.inspect :as inspect]
            [vim-clj.nvim.core :as nvim]
            [vim-clj.nvim.methods :as methods])
  (:gen-class))

(defn -main
  "I don't do a whole lot ... yet."
  []
  (nvim/connect-to-stdin!)

  (nvim/api-call set-var-async nvim/is-running-var 1 nil)
  (nvim/out-writeln "VimNrepl is up")
  (methods/register-methods!)

  (loop []
    (Thread/sleep 1000)
    (when @methods/should-shutdown
      (nvim/api-call set-var-async nvim/is-running-var 0 nil)
      (System/exit 0))
    (recur)))
