(ns game.util.format
  "Форматирование вывода для разных клиентов (Web/Telnet)"
  (:require [clojure.string :as str]
            [game.world :as world]
            [game.items :as items]))

;; ========== ОБЩЕЕ ФОРМАТИРОВАНИЕ ==========

(defn wrap-text
  "Перенос текста по ширине"
  [text width]
  (when text
    (->> (str/split text #"\s+")
         (reduce (fn [lines word]
                   (let [current-line (last lines)]
                     (if (< (count (str current-line " " word)) width)
                       (conj (pop lines) (str current-line " " word))
                       (conj lines word))))
                 [""])
         (str/join "\n"))))

(defn format-list
  "Форматирование списка элементов"
  [items & {:keys [empty-message] :or {empty-message "пусто"}}]
  (if (empty? items)
    empty-message
    (str/join ", " items)))

(defn format-table
  "Табличное форматирование"
  [headers rows]
  (let [col-widths (map (fn [idx]
                          (apply max (count (nth headers idx))
                                 (map #(count (str (nth % idx))) rows)))
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

;; ========== ФОРМАТИРОВАНИЕ ДЛЯ WEB (HTML) ==========

(defn web-format-room
  "Форматирование комнаты для веб-интерфейса"
  [room-key]
  (let [room (world/get-room room-key)
        items (world/get-room-items room-key)
        players (world/get-room-players room-key)]
    
    {:html (str "<div class='room'>"
                "<h2>" (:name room) "</h2>"
                "<p class='description'>" (:desc room) "</p>"
                "<div class='section'>"
                "<h3>📦 Предметы:</h3>"
                "<ul class='items'>"
                (if (empty? items)
                  "<li>Нет предметов</li>"
                  (str/join "" (map #(str "<li>" (items/get-item-name %) "</li>") items)))
                "</ul>"
                "</div>"
                "<div class='section'>"
                "<h3>👥 Игроки:</h3>"
                "<ul class='players'>"
                (if (empty? players)
                  "<li>Нет игроков</li>"
                  (str/join "" (map #(str "<li>" % "</li>") players)))
                "</ul>"
                "</div>"
                "</div>")
     
     :json {:name (:name room)
            :desc (:desc room)
            :items (map items/get-item-name items)
            :players (vec players)}}))

(defn web-format-inventory
  "Форматирование инвентаря для веба"
  [player-name]
  (let [inventory (world/get-player-inventory player-name)]
    {:html (if (empty? inventory)
             "<p class='empty'>Ваш инвентарь пуст</p>"
             (str "<ul class='inventory'>"
                  (str/join "" (map #(str "<li>" (items/get-item-name %) "</li>") inventory))
                  "</ul>"))
     
     :json {:items (vec (map items/get-item-name inventory))
            :count (count inventory)}}))

(defn web-format-chat-message
  "Форматирование сообщения чата"
  [from message timestamp]
  (let [time-str (.format (java.text.SimpleDateFormat. "HH:mm:ss") 
                         (java.util.Date. timestamp))]
    {:html (str "<div class='chat-message' data-from='" from "'>"
                "<span class='time'>[" time-str "]</span> "
                "<span class='player'>" from ":</span> "
                "<span class='text'>" message "</span>"
                "</div>")
     
     :json {:from from
            :message message
            :timestamp timestamp
            :time time-str}}))

(defn web-format-command-help
  "Форматирование справки по командам"
  []
  {:html (str "<div class='help'>"
              "<h3>📖 Доступные команды:</h3>"
              "<table class='commands-table'>"
              "<tr><th>Команда</th><th>Описание</th></tr>"
              "<tr><td><code>look</code></td><td>Осмотреться</td></tr>"
              "<tr><td><code>идти [направление]</code></td><td>Переместиться</td></tr>"
              "<tr><td><code>взять [предмет]</code></td><td>Взять предмет</td></tr>"
              "<tr><td><code>положить [предмет]</code></td><td>Положить предмет</td></tr>"
              "<tr><td><code>инвентарь</code></td><td>Показать инвентарь</td></tr>"
              "<tr><td><code>сказать [текст]</code></td><td>Сказать в комнате</td></tr>"
              "<tr><td><code>помощь</code></td><td>Эта справка</td></tr>"
              "<tr><td><code>выход</code></td><td>Выйти из игры</td></tr>"
              "</table>"
              "</div>")
   
   :json {:commands [{:cmd "look" :desc "Осмотреться"}
                     {:cmd "идти [направление]" :desc "Переместиться"}
                     {:cmd "взять [предмет]" :desc "Взять предмет"}
                     {:cmd "положить [предмет]" :desc "Положить предмет"}
                     {:cmd "инвентарь" :desc "Показать инвентарь"}
                     {:cmd "сказать [текст]" :desc "Сказать в комнате"}
                     {:cmd "помощь" :desc "Справка по командам"}
                     {:cmd "выход" :desc "Выйти из игры"}]}})

;; ========== ФОРМАТИРОВАНИЕ ДЛЯ TELNET (plain text) ==========

(defn telnet-format-room
  "Форматирование комнаты для telnet"
  [room-key]
  (let [room (world/get-room room-key)
        items (world/get-room-items room-key)
        players (world/get-room-players room-key)]
    
    (str "╔═══════════════════════════════════════╗\n"
         "║ " (:name room) (str/join (repeat (- 37 (count (:name room))) " ")) "║\n"
         "╠═══════════════════════════════════════╣\n"
         "║ " (wrap-text (:desc room) 37) "\n"
         "║\n"
         "║ 📦 Предметы: " (format-list (map items/get-item-name items) :empty-message "нет") "\n"
         "║ 👥 Игроки: " (format-list (vec players) :empty-message "никого") "\n"
         "╚═══════════════════════════════════════╝")))

(defn telnet-format-inventory
  "Форматирование инвентаря для telnet"
  [player-name]
  (let [inventory (world/get-player-inventory player-name)]
    (if (empty? inventory)
      "Ваш инвентарь пуст."
      (str "╔══════════════════════════════╗\n"
           "║        ВАШ ИНВЕНТАРЬ        ║\n"
           "╠══════════════════════════════╣\n"
           (str/join "\n" (map-indexed 
                            (fn [idx item] 
                              (str "║ " (inc idx) ". " (items/get-item-name item) 
                                   (str/join (repeat (- 28 (count (items/get-item-name item))) " ")) "║"))
                            inventory))
           "\n╚══════════════════════════════╝"))))

(defn telnet-format-chat-message
  "Форматирование сообщения чата для telnet"
  [from message timestamp]
  (let [time-str (.format (java.text.SimpleDateFormat. "HH:mm:ss") 
                         (java.util.Date. timestamp))]
    (str "[" time-str "] " from ": " message)))

(defn telnet-format-command-help
  "Форматирование справки для telnet"
  []
  (str "ДОСТУПНЫЕ КОМАНДЫ:\n"
       (format-table ["Команда" "Описание"]
                     [["look / осмотреть" "Осмотреться"]
                      ["идти [направление]" "Переместиться (север/юг/запад/восток)"]
                      ["взять [предмет]" "Взять предмет"]
                      ["положить [предмет]" "Положить предмет"]
                      ["инвентарь" "Показать инвентарь"]
                      ["сказать [текст]" "Сказать в комнате"]
                      ["помощь" "Эта справка"]
                      ["выход" "Выйти из игры"]])))

;; ========== УНИВЕРСАЛЬНЫЕ ФУНКЦИИ ==========

(defn format-output
  "Универсальное форматирование вывода"
  [content type & {:keys [client-type] :or {client-type :web}}]
  (case client-type
    :web (if (= type :room)
           (web-format-room content)
           content)
    :telnet (if (= type :room)
              (telnet-format-room content)
              content)
    content))

(defn init-format
  "Инициализация модуля форматирования"
  []
  (println "[FORMAT] Модуль форматирования инициализирован"))

;; Автоматическая инициализация
(init-format)

;; Примеры использования
(comment
  ;; Для веб-интерфейса
  (web-format-room :laboratory)
  (web-format-inventory "Игрок1")
  (web-format-chat-message "Алексей" "Привет всем!" (System/currentTimeMillis))
  
  ;; Для telnet
  (telnet-format-room :laboratory)
  (telnet-format-inventory "Игрок1")
  (telnet-format-chat-message "Мария" "Идем в архив!" (System/currentTimeMillis))
  
  ;; Универсально
  (format-output :laboratory :room :client-type :web)
  (format-output :laboratory :room :client-type :telnet)
)