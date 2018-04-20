(ns vim-nrepl.core
  (:require [neovim-client.1.api :as api]
            [neovim-client.nvim :as nvim-client]
            [clojure.tools.nrepl :as nrepl]
            [vim-nrepl.inspect :as inspect]
            [vim-nrepl.api :as api-ext])
  (:gen-class))

(defonce is-running-var "vim_nrepl_is_running")
(defonce channel-var "vim_nrepl_channel")

(defonce nvim (atom {}))

(defonce should-shutdown (atom false))

(defn set-is-running [val]
  (api/set-var-async @nvim is-running-var (int val) (fn [& _])))

(defn echo [msg]
  (api/command-async @nvim (str "echo '" (clojure.string/replace msg "'", "''") "'") (fn [_])))

(defn nrepl-connect [port & {:keys [host] :or {host "localhost"}}]
  (let [client (-> (nrepl/connect :host host :port port)
                   (nrepl/client 5000))
        session (nrepl/new-session client)]
    (nrepl/client-session client :session session)))

(defn shutdown [& _]
  (reset! should-shutdown true))

(defn clj-file-ns [msg]
  (let [{:keys [args]} (api-ext/msg->map msg)]
    (try
      (inspect/read-ns-name (first args))
      (catch Exception _ nil))))

(defn register-methods! []
  (nvim-client/register-method! @nvim "shutdown" #'shutdown)
  (nvim-client/register-method! @nvim "clj-file-ns" #'clj-file-ns))

(defn init! []
  (register-methods!)
  (set-is-running 1)
  (let [[channel] (api/get-api-info @nvim)]
    (api/set-var @nvim channel-var channel)))

(defn connect-to-stdin! []
  (reset! nvim (nvim-client/new 1))
  (init!))

(defn connect-to-port! [port]
  (reset! nvim (nvim-client/new 1 "127.0.0.1" (int port)))
  (init!))

(defn -main
  "I don't do a whole lot ... yet."
  []
  (connect-to-stdin!)

  (echo "VimNrepl is up")

  (loop []
    (Thread/sleep 1000)
    (when @should-shutdown
      (set-is-running 0)
      (System/exit 0))
    (recur)))
