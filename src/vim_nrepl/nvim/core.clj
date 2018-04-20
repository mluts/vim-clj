(ns vim-nrepl.nvim.core
  (:require [neovim-client.1.api :as nvim-api]
            [neovim-client.nvim :as nvim-client]))

(defonce nvim (atom {}))

(defonce is-running-var "vim_nrepl_is_running")
(defonce channel-var "vim_nrepl_channel")

(defn msg->map [[msg-type argc method args]]
  {:msg-type  msg-type
   :argc      argc
   :method    method
   :args      args})

(defn register-method! [m f]
  (nvim-client/register-method! @nvim m f))

(defn command [& args]
  (apply nvim-api/command @nvim args))

(defn command-async [& args]
  (apply nvim-api/command-async @nvim args))

(defn call-function [& args]
  (apply nvim-api/call-function @nvim args))

(defn call-function-async [& args]
  (apply nvim-api/call-function-async @nvim args))

(defn set-var [& args]
  (apply nvim-api/set-var @nvim args))

(defn set-var-async [& args]
  (apply nvim-api/set-var-async @nvim args))

(defn connect-to-stdin! []
  (reset! nvim (nvim-client/new 1)))

(defn connect-to-port! [port]
  (reset! nvim (nvim-client/new 1 "127.0.0.1" (int port))))

(defn set-is-running [val]
  (nvim-api/set-var-async @nvim is-running-var (int val) (fn [& _])))

(defn set-channel-var []
  (nvim-api/get-api-info-async @nvim (fn [[channel]] (set-var-async channel-var channel (fn [& _])))))

(defn echo [msg]
  (let [msg (clojure.string/replace msg "'" "''")]
    (command-async (str "echo '" msg "'") (fn [& _]))))
