(ns vim-clj.core
  (:require [vim-clj.inspect :as inspect]
            [vim-clj.nvim.core :as nvim]
            [vim-clj.nvim.methods :as methods])
  (:gen-class))

(defn -main
  "I don't do a whole lot ... yet."
  []
  (nvim/connect-to-stdin!)

  (nvim/echo "VimNrepl is up")

  (loop []
    (Thread/sleep 1000)
    (when @methods/should-shutdown
      (nvim/set-var-async nvim/is-running-var 0 (fn [& _]))
      (System/exit 0))
    (recur)))
