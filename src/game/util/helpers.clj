(ns game.util.helpers
  "Вспомогательные функции и утилиты для работы с STM и игровым миром.
   Основано на оригинальном mire/util.clj с расширениями."
  (:require [game.game.world :as world]
            [game.players.state :as players]
            [game.players.inventory :as inventory]
            [clojure.string :as str]))

;; СТРОКОВЫЕ ОПЕРАЦИИ
(defn trim
  "Убрать пробелы в начале и конце строки"
  [s]
  (str/trim s))

(defn lower-case
  "Привести строку к нижнему регистру"
  [s]
  (when s
    (str/lower-case s)))

(defn capitalize-first
  "Сделать первую букву заглавной"
  [s]
  (when (and s (not (empty? s)))
    (str (str/upper-case (subs s 0 1))
         (subs s 1))))

(defn words
  "Разбить строку на слова"
  [s]
  (when s
    (str/split (trim s) #"\s+")))

(defn unwords
  "Собрать слова в строку"
  [words]
  (str/join " " words))

;; ФУНКЦИИ ДЛЯ РАБОТЫ С КОМАНДАМИ
(defn parse-command
  "Разобрать команду игрока"
  [input]
  (when-not (empty? input)
    (let [trimmed (trim input)
          parts (words trimmed)]
      {:raw input
       :command (first parts)
       :args (rest parts)
       :arg-count (count (rest parts))
       :full-args (str/join " " (rest parts))})))

(defn normalize-command
  "Нормализовать команду (нижний регистр, синонимы)"
  [command]
  (let [cmd (lower-case command)
        synonyms {
          "с" "север"
          "ю" "юг"
          "з" "запад"
          "в" "восток"
          "и" "инвентарь"
          "осм" "осмотреть"
          "вз" "взять"
          "пол" "положить"
          "ска" "сказать"
          "пом" "помощь"
          "вых" "выход"
        }]
    (get synonyms cmd cmd)))

(defn validate-command
  "Проверить валидность команды"
  [parsed allowed-commands]
  (let [cmd (:command parsed)]
    (when cmd
      (let [normalized (normalize-command cmd)]
        (contains? (set allowed-commands) normalized)))))

;; ФУНКЦИИ ДЛЯ РАБОТЫ С ПРЕДМЕТАМИ
(defn find-item-in-room
  "Найти предмет в комнате по части имени"
  [room-name item-pattern]
  (let [room-items (world/get-room-items room-name)
        pattern (lower-case item-pattern)]
    (first (filter #(str/includes? (lower-case %) pattern) room-items))))

(defn find-item-in-inventory
  "Найти предмет в инвентаре по части имени"
  [player-name item-pattern]
  (let [inv-items (inventory/get-inventory player-name)
        pattern (lower-case item-pattern)]
    (first (filter #(str/includes? (lower-case %) pattern) inv-items))))

(defn format-item-list
  "Отформатировать список предметов"
  [items]
  (if (empty? items)
    "ничего"
    (str/join ", " (map #(get-in (world/get-item-info %) [:name] %) items))))

;; ФУНКЦИИ ДЛЯ РАБОТЫ С КОМНАТАМИ
(defn format-room-name
  "Отформатировать имя комнаты"
  [room-key]
  (let [room (world/get-room room-key)]
    (or (:name room) (name room-key))))

(defn format-room-description
  "Отформатировать описание комнаты"
  [room-key]
  (let [room (world/get-room room-key)]
    (str (:desc room)
         "\n\n"
         "Выходы: " (format-exits room-key)
         "\n"
         "Предметы: " (format-item-list (world/get-room-items room-key)))))

(defn format-exits
  "Отформатировать выходы из комнаты"
  [room-key]
  (let [exits (world/get-available-exits room-key)]
    (if (empty? exits)
      "нет"
      (str/join ", " 
                (map (fn [[dir info]]
                       (str (name dir) 
                            (when (:locked info) " (закрыт)")))
                     exits)))))

(defn get-direction-keyword
  "Преобразовать строку направления в ключевое слово"
  [direction]
  (case (lower-case direction)
    "север" :north
    "юг" :south
    "запад" :west
    "восток" :east
    "с" :north
    "ю" :south
    "з" :west
    "в" :east
    "north" :north
    "south" :south
    "west" :west
    "east" :east
    "n" :north
    "s" :south
    "w" :west
    "e" :east
    nil))

;; ФУНКЦИИ ДЛЯ РАБОТЫ С ИГРОКАМИ
(defn get-player-display-name
  "Получить отображаемое имя игрока"
  [player-name]
  (capitalize-first player-name))

(defn format-player-list
  "Отформатировать список игроков"
  [players]
  (if (empty? players)
    "никого"
    (str/join ", " (map get-player-display-name players))))

(defn get-players-in-same-room
  "Получить других игроков в той же комнате"
  [player-name]
  (let [room (world/get-player-room player-name)]
    (disj (world/get-room-players room) player-name)))

;; STM УТИЛИТЫ
(defn with-stm-transaction
  "Выполнить код внутри STM транзакции с обработкой ошибок"
  [f & args]
  (try
    (dosync
      (apply f args))
    (catch Exception e
      (println "[STM ERROR]" (.getMessage e))
      nil)))

(defn atomic-update
  "Атомарно обновить несколько refs"
  [updates]
  (dosync
    (doseq [[ref f & args] updates]
      (apply alter ref f args))))

(defn ensure-ref
  "Создать ref если не существует"
  [ref-map key initial-value]
  (dosync
    (when-not (get @ref-map key)
      (alter ref-map assoc key (ref initial-value)))
    (get @ref-map key)))

;; ФОРМАТИРОВАНИЕ ВЫВОДА
(defn wrap-text
  "Перенос текста по ширине"
  [text width]
  (when text
    (loop [remaining text
           result []]
      (if (<= (count remaining) width)
        (str/join "\n" (conj result remaining))
        (let [space-idx (str/last-index-of remaining " " width)
              break-idx (if (and space-idx (> space-idx 0)) space-idx width)
              line (subs remaining 0 break-idx)
              next-remaining (subs remaining (min (inc break-idx) (count remaining)))]
          (recur next-remaining (conj result line)))))))

(defn format-table
  "Отформатировать данные как таблицу"
  [headers rows]
  (let [col-widths (map (fn [header]
                          (apply max (count header)
                                 (map #(count (str (nth %1 %2))) rows)))
                        (range (count headers)))]
    (str/join "\n"
              (concat
                [(str/join " | " (map #(format (str "%-" % "s") %2) 
                                     col-widths headers))]
                [(str/join "-+-" (map #(str/join (repeat % "-")) col-widths))]
                (map (fn [row]
                       (str/join " | " (map #(format (str "%-" % "s") (str %2)) 
                                           col-widths row)))
                     rows)))))

;; ВАЛИДАЦИЯ
(defn valid-player-name?
  "Проверить валидность имени игрока"
  [name]
  (and name
       (not (empty? (trim name)))
       (re-matches #"^[a-zA-Zа-яА-Я0-9_-]{2,20}$" name)
       (not (re-matches #".*[<>\"'].*" name))))

(defn valid-item-name?
  "Проверить валидность имени предмета"
  [name]
  (and name
       (not (empty? (trim name)))
       (<= (count name) 50)))

;; СЛУЖЕБНЫЕ ФУНКЦИИ
(defn random-element
  "Выбрать случайный элемент из коллекции"
  [coll]
  (when (seq coll)
    (nth coll (rand-int (count coll)))))

(defn shuffle-seq
  "Перемешать последовательность"
  [coll]
  (let [alist (java.util.ArrayList. coll)]
    (java.util.Collections/shuffle alist)
    (seq alist)))

(defn now
  "Текущее время в миллисекундах"
  []
  (System/currentTimeMillis))

(defn elapsed-time
  "Прошедшее время в секундах"
  [start-time]
  (/ (- (now) start-time) 1000.0))

;; ЛОГИРОВАНИЕ
(defn log-action
  "Записать действие в лог"
  [player-name action & args]
  (let [timestamp (java.time.LocalDateTime/now)
        formatted-args (str/join " " args)]
    (println (format "[%s] %s: %s %s" 
                     (.format timestamp (java.time.format.DateTimeFormatter/ofPattern "HH:mm:ss"))
                     player-name
                     action
                     formatted-args))))

;; ЭКСПОРТ
(defn init-helpers
  "Инициализировать утилиты"
  []
  (println "[UTIL] Вспомогательные функции инициализированы"))

(init-helpers)
