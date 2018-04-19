(ns vim-nrepl.core
  (:require [neovim-client.1.api :as api]
            [neovim-client.nvim :as nvim-client]
            [clojure.tools.nrepl.server :refer [start-server]]
            [clojure.tools.nrepl :as nrepl])
  (:gen-class))

(def is-running-var "vim_nrepl_is_running")
(def nvim (atom {}))
(def should-shutdown (atom false))

(defn set-is-running [val]
  (api/set-var @nvim is-running-var (int val)))

(defn echo [msg]
  (api/command-async @nvim (str "echo '" msg "'") #()))

(defn nrepl-connect [port & {:keys [host] :or {host "localhost"}}]
  (let [client (-> (nrepl/connect :host host :port port)
                   (nrepl/client 5000))
        session (nrepl/new-session client)]
    (nrepl/client-session client :session session)))

(defn shutdown [& args]
  (reset! should-shutdown true))

(defn -main
  "I don't do a whole lot ... yet."
  []
  (reset! nvim (nvim-client/new 1))

  (start-server :bind "0.0.0.0" :port 31313)

  (nvim-client/register-method! @nvim "shutdown" shutdown)

  (set-is-running 1)
  (echo "VimNrepl is up")

  (loop []
    (Thread/sleep 1000)
    (when @should-shutdown
      (set-is-running 0)
      (System/exit 0))
    (recur)))
