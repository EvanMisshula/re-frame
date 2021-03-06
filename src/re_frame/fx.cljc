(ns re-frame.fx
  (:require
    [re-frame.router      :as router]
    [re-frame.db          :refer [app-db]]
    [re-frame.interceptor :refer [->interceptor]]
    [re-frame.interop     :refer [set-timeout!]]
    [re-frame.events      :as events]
    [re-frame.registrar   :refer [get-handler clear-handlers register-handler]]
    [re-frame.loggers     :refer [console]]))


;; -- Registration ------------------------------------------------------------

(def kind :fx)
(assert (re-frame.registrar/kinds kind))
(def register  (partial register-handler kind))

;; -- Interceptor -------------------------------------------------------------

(def do-fx
  "An interceptor whose `:after` executes the instructions in `:effects`.

  For each key in `:effects` (a map), it calls the registered `effects handler`
  (see `reg-fx` for registration of effects handlers).

  So, if `:effects` was:
      {:dispatch  [:hello 42]
       :db        {...}
       :undo      \"set flag\"}

  it will call the registered effects handlers for each of the map's keys:
  `:dispatch`, `:undo` and `:db`. When calling each handler, provides the map
  value for that key - so in the example above the effect handler for :dispatch
  will be given one arg `[:hello 42]`.

  You cannot rely on the ordering in which effects are executed."
  (->interceptor
    :id :do-fx
    :after (fn do-fx-after
             [context]
             (doseq [[effect-key effect-value] (:effects context)]
               (if-let [effect-fn (get-handler kind effect-key true)]
                 (effect-fn effect-value)
                 (console :error "re-frame: no handler registered for effect: \"" effect-key "\". Ignoring."))))))

;; -- Builtin Effect Handlers  ------------------------------------------------

;; :dispatch-later
;;
;; `dispatch` one or more events after given delays. Expects a collection
;; of maps with two keys:  :`ms` and `:dispatch`
;;
;; usage:
;;
;;    {:dispatch-later [{:ms 200 :dispatch [:event-id "param"]}    ;;  in 200ms do this: (dispatch [:event-id "param"])
;;                      {:ms 100 :dispatch [:also :this :in :100ms]}]}
;;
(register
  :dispatch-later
  (fn [value]
    (doseq [{:keys [ms dispatch] :as effect} value]
        (if (or (empty? dispatch) (not (number? ms)))
          (console :error "re-frame: ignoring bad :dispatch-later value:" effect)
          (set-timeout! #(router/dispatch dispatch) ms)))))


;; :dispatch
;;
;; `dispatch` one event. Excepts a single vector.
;;
;; usage:
;;   {:dispatch [:event-id "param"] }

(register
  :dispatch
  (fn [value]
    (if-not (vector? value)
      (console :error "re-frame: ignoring bad :dispatch value. Expected a vector, but got:" value)
      (router/dispatch value))))


;; :dispatch-n
;;
;; `dispatch` more than one event. Expects a list or vector of events. Something for which
;; sequential? returns true.
;;
;; usage:
;;   {:dispatch-n (list [:do :all] [:three :of] [:these])}
;;
;; Note: nil events are ignored which means events can be added
;; conditionally:
;;    {:dispatch-n (list (when (> 3 5) [:conditioned-out])
;;                       [:another-one])}
;;
(register
  :dispatch-n
  (fn [value]
    (if-not (sequential? value)
      (console :error "re-frame: ignoring bad :dispatch-n value. Expected a collection, got got:" value)
      (doseq [event (remove nil? value)] (router/dispatch event)))))


;; :deregister-event-handler
;;
;; removes a previously registered event handler. Expects either a single id (
;; typically a namespaced keyword), or a seq of ids.
;;
;; usage:
;;   {:deregister-event-handler :my-id)}
;; or:
;;   {:deregister-event-handler [:one-id :another-id]}
;;
(register
  :deregister-event-handler
  (fn [value]
    (let [clear-event (partial clear-handlers events/kind)]
      (if (sequential? value)
        (doseq [event value] (clear-event event))
        (clear-event value)))))


;; :db
;;
;; reset! app-db with a new value. `value` is expected to be a map.
;;
;; usage:
;;   {:db  {:key1 value1 key2 value2}}
;;
(register
  :db
  (fn [value]
    (if-not (identical? @app-db value)
      (reset! app-db value))))

