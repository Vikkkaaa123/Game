(ns game.core
  "Основная логика игры. Без консольного ввода/вывода."
  (:require [game.world :as world]
            [game.items :as items]
            [game.players.inventory :as inventory]
            [game.players.state :as player-state]
            [game.players.chat :as chat]
            [game.puzzles :as puzzles]  
            [clojure.string :as str]))

;; ========== ВСПОМОГАТЕЛЬНЫЕ ФУНКЦИИ С ЗАЩИТОЙ ОТ NIL ==========

(defn- safe-get-item-name
  "Безопасное получение имени предмета"
  [item-key]
  (if item-key
    (or (items/get-item-name item-key) (str item-key))
    "неизвестный предмет"))

(defn- safe-get-room
  "Безопасное получение комнаты"
  [room-key]
  (if room-key
    (or (world/get-room room-key) {:name "Неизвестная комната" :desc "Комната не найдена"})
    {:name "Ошибка" :desc "Комната не определена"}))

(defn- format-room-description
  "Подробное описание комнаты"
  [room-key player-name]
  (let [room (safe-get-room room-key)
        room-items (world/get-room-items room-key)
        players (world/get-room-players room-key)
        other-players (if players (disj players player-name) #{})]
    
    (str "╔════════════════════════════════════════╗\n"
         "║ " (:name room) (str/join (repeat (- 38 (count (:name room))) " ")) "║\n"
         "╠════════════════════════════════════════╣\n"
         "║ " (:desc room) "\n"
         "║\n"
         "║ 📦 ПРЕДМЕТЫ: " (if (and room-items (seq room-items))
                             (->> room-items
                                  (map safe-get-item-name)
                                  (str/join ", "))
                             "нет") "\n"
         "║ 👥 ИГРОКИ: " (if (seq other-players)
                           (str/join ", " other-players)
                           "никого") "\n"
         "║ 🚪 ВЫХОДЫ: " (if-let [exits (world/get-room-exits room-key)]
                           (str/join ", " (map name (keys exits)))
                           "нет") "\n"
         "╚════════════════════════════════════════╝")))

(defn- format-inventory
  "Форматирование инвентаря"
  [player-name]
  (let [inv-items (world/get-player-inventory player-name)]
    (if (and inv-items (seq inv-items))
      (str "🎒 ВАШ ИНВЕНТАРЬ (" (count inv-items) "):\n"
           (str/join "\n" 
                     (map-indexed (fn [idx item-key]
                                    (str (inc idx) ". " 
                                         (safe-get-item-name item-key)))
                                  inv-items)))
      "📭 Ваш инвентарь пуст.")))

;; ========== ОСНОВНАЯ ФУНКЦИЯ КОМАНД С ЗАЩИТОЙ ==========

(defn handle-command [player-name input]
  (try
    ;; Проверяем существование игрока
    (if-not (world/player-exists? player-name)
      "❌ Игрок не найден. Переподключитесь."
      
      (do
        ;; Обновляем статистику
        (try
          (player-state/update-last-action player-name)
          (player-state/increment-commands player-name)
          (catch Exception _ nil))
        
        (let [parts (str/split (str/trim input) #"\s+")
              command (when (seq parts) (str/lower-case (first parts)))
              args (str/join " " (rest parts))
              current-room (world/get-player-room player-name)]
          
          (cond
            ;; ========== ОСМОТР ==========
            (and command (contains? #{"look" "осмотреть" "посмотреть" "осмотреться" "l"} command))
            (format-room-description current-room player-name)
            
            ;; ========== ИНВЕНТАРЬ ==========
            (and command (contains? #{"инвентарь" "inventory" "инв" "i"} command))
            (format-inventory player-name)
            
            ;; ========== ВЗЯТЬ ПРЕДМЕТ ==========
            (and command (contains? #{"взять" "take" "поднять" "t"} command))
            (if (str/blank? args)
              "❌ Укажите предмет: взять [предмет]"
              (let [result (inventory/take-item! player-name args)]
                (if (:success result)
                  (do
                    (try (player-state/increment-items-taken player-name) (catch Exception _ nil))
                    (str "✅ " (:message result)))
                  (str "❌ " (:message result)))))
            
            ;; ========== ПОЛОЖИТЬ ПРЕДМЕТ ==========
            (and command (contains? #{"положить" "drop" "бросить" "пол"} command))
            (if (str/blank? args)
              "❌ Укажите предмет: положить [предмет]"
              (let [result (inventory/drop-item! player-name args)]
                (if (:success result)
                  (str "✅ " (:message result))
                  (str "❌ " (:message result)))))
            
            ;; ========== ИССЛЕДОВАТЬ ПРЕДМЕТ ==========
            ;; В команде "осмотреть [предмет]" добавьте:
(and command (contains? #{"осмотреть" "examine" "рассмотреть" "исследовать" "ex"} command))
(if (str/blank? args)
  "❌ Укажите предмет: осмотреть [предмет]"
  (let [item-key (inventory/find-item player-name args)]
    (if item-key
      ;; Проверяем специальные предметы для головоломок
      (cond
        (= item-key :microscope)
        (let [result (puzzles/examine-microscope player-name)]
          (if (:success result)
            (str "🔬 " (:message result))
            (str "❌ " (:message result))))
        
        (= item-key :journal)
        (let [result (puzzles/find-lab-date player-name)]
          (if (:success result)
            (str "📖 " (:message result))
            (str "❌ " (:message result))))
        
        :else
        ;; Обычный предмет
        (let [item-data (items/get-item item-key)]
          (if item-data
            (str "🔍 Вы внимательно осматриваете " (:name item-data) ":\n"
                 (or (:examination-text item-data)
                     (:desc item-data)
                     "Особенностей не обнаружено."))
            "❌ Информация о предмете не найдена")))
      "❌ У вас нет такого предмета")))
            
            ;; ========== ИСПОЛЬЗОВАТЬ ПРЕДМЕТ ==========
            (and command (contains? #{"использовать" "use" "применить" "u"} command))
            (if (str/blank? args)
              "❌ Укажите предмет: использовать [предмет]"
              (let [result (inventory/use-item! player-name args)]
                (if (:success result)
                  (:message result)
                  (str "❌ " (:message result)))))

                        ;; ========== ИСПОЛЬЗОВАТЬ ПРЕДМЕТ ДЛЯ ГОЛОВОЛОМОК ==========
            (and command (contains? #{"использовать" "use" "применить" "u"} command))
            (if (str/blank? args)
              "❌ Укажите предмет: использовать [предмет]"
              (let [room (world/get-player-room player-name)
                    inventory (world/get-player-inventory player-name)]
                (cond
                  ;; ПОЧИНИТЬ КОНСОЛЬ (в серверной)
                  (and (= room :console_room)
                       (or (str/includes? (str/lower-case args) "провод")
                           (str/includes? (str/lower-case args) "wire")))
                  (if (some #{:blueprint} inventory)
                    (if (some #{:wire} inventory)
                      (str "✅ Вы подключили провод к консоли, следуя схеме!\n"
                           "Теперь введите код: кодировка 3107")
                      "❌ У вас нет провода!")
                    "❌ Нужна схема подключения! Возьмите её в коридоре.")
                  
                  (and (= room :console_room)
                       (or (str/includes? (str/lower-case args) "схем")
                           (str/includes? (str/lower-case args) "blueprint")))
                  (if (some #{:wire} inventory)
                    (if (some #{:blueprint} inventory)
                      (str "✅ Вы использовали схему для подключения провода!\n"
                           "Теперь введите код: кодировка 3107")
                      "❌ У вас нет схемы!")
                    "❌ Нужен провод! Возьмите его в лаборатории.")
                  
                  ;; КЛЮЧ-КАРТА (в коридоре для архива)
                  (and (= room :hallway_ru)
                       (or (str/includes? (str/lower-case args) "ключ")
                           (str/includes? (str/lower-case args) "keycard")))
                  (if (some #{:keycard} inventory)
                    (str "🔑 Вы приложили ключ-карту к считывателю.\n"
                         "Теперь введите код доступа: кодировка 3107")
                    "❌ У вас нет ключ-карты!")
                  
                  ;; ПО УМОЛЧАНИЮ
                  :else
                  "Вы использовали предмет, но ничего особенного не произошло.")))

            ;; ========== ВВОД КОДА ==========
            (and command (contains? #{"кодировка" "code" "ввести" "код"} command))
            (let [room (world/get-player-room player-name)]
              (cond
                ;; КОД ДЛЯ АРХИВА (в коридоре)
                (and (= room :hallway_ru) (= args "3107"))
                (str "🔓 Код принят! Дверь в архив открыта!\n"
                     "Теперь можете перейти в архив: идти запад")
                
                ;; КОД ДЛЯ КОНСОЛИ (в серверной)
                (and (= room :console_room) (= args "3107"))
                (do
                  ;; Проверяем, есть ли провод и схема
                  (let [inventory (world/get-player-inventory player-name)]
                    (if (and (some #{:wire} inventory) (some #{:blueprint} inventory))
                      (str "🎉 ПОБЕДА! Консоль активирована!\n"
                           "Двери лаборатории открыты! Вы выбрались!")
                      "❌ Нужно сначала подключить провод и схему!")))
                
                ;; НЕВЕРНЫЙ КОД
                (and (or (= room :hallway_ru) (= room :console_room)) (not (str/blank? args)))
                "❌ Неверный код!"
                
                :else
                "❌ Здесь нет кодовой панели"))

            ;; ========== ПРОВЕРКА ПОБЕДЫ ==========
            (and command (contains? #{"победа" "побег" "escape" "win"} command))
            (let [inventory (world/get-player-inventory player-name)
                  room (world/get-player-room player-name)]
              (if (and (= room :console_room)
                       (some #{:wire} inventory)
                       (some #{:blueprint} inventory))
                (str "🎯 ВЫ ПОБЕДИЛИ! У вас есть все для побега:\n"
                     "1. Провод для подключения ✓\n"
                     "2. Схема подключения ✓\n"
                     "3. Вы в серверной ✓\n\n"
                     "Используйте: использовать провод\n"
                     "Затем: кодировка 3107")
                (str "🔍 Условия победы:\n"
                     "1. Быть в серверной (вы в " (world/get-room-name room) ")\n"
                     "2. Иметь провод (из лаборатории)\n"
                     "3. Иметь схему подключения (из коридора)\n"
                     "4. Ввести код 3107\n\n"
                     "Что у вас есть: " 
                     (if (some #{:wire} inventory) "провод ✓" "провод ✗") ", "
                     (if (some #{:blueprint} inventory) "схема ✓" "схема ✗"))))
            
            ;; ========== ЧАТ ==========
            (and command (contains? #{"сказать" "say" "с"} command))
            (if (str/blank? args)
              "❌ Скажите что-нибудь: сказать [текст]"
              (let [result (chat/say-to-room! player-name args)]
                (if (:success result)
                  {:type :chat-broadcast
                   :from player-name
                   :message args
                   :broadcast-to (:broadcast-to result)}
                  (str "❌ " (:message result)))))
            
            ;; ========== ПЕРЕМЕЩЕНИЕ (упрощенное) ==========
            (and command (contains? #{"идти" "go" "g" "с" "ю" "з" "в" "n" "s" "w" "e"} command))
            (let [direction-map {"с" :north "ю" :south "з" :west "в" :east
                                "n" :north "s" :south "w" :west "e" :east
                                "север" :north "юг" :south "запад" :west "восток" :east
                                "north" :north "south" :south "west" :west "east" :east}
                  dir-arg (if (contains? #{"с" "ю" "з" "в" "n" "s" "w" "e"} command)
                            command
                            args)
                  direction (get direction-map (str/lower-case dir-arg))
                  target-room (when direction (world/get-exit-room current-room direction))]
              
              (if target-room
                (do
                  (world/set-player-room! player-name target-room)
                  (try (player-state/add-visited-room player-name target-room) (catch Exception _ nil))
                  (format-room-description target-room player-name))
                (str "❌ Нельзя пойти в этом направлении.")))
            
            ;; ========== ПОМОЩЬ ==========
            (and command (contains? #{"помощь" "help" "справка" "h" "?"} command))
            (str "📖 ДОСТУПНЫЕ КОМАНДЫ:\n\n"
                 "look / осмотреть - осмотреться\n"
                 "взять [предмет] / take - взять предмет\n"
                 "положить [предмет] / drop - положить предмет\n"
                 "инвентарь / inventory - показать инвентарь\n"
                 "идти [направление] / go - переместиться\n"
                 "с/ю/з/в / n/s/w/e - короткие команды движения\n"
                 "сказать [текст] / say - сказать в комнате\n"
                 "помощь / help - эта справка\n"
                 "выход / exit - выйти из игры")
            
            ;; ========== ВЫХОД ==========
            (and command (contains? #{"выход" "exit" "quit" "выйти" "q"} command))
            (do
              (world/remove-player! player-name)
              "👋 Вы вышли из игры")
            
            ;; ========== НЕИЗВЕСТНАЯ КОМАНДА ==========
            :else
            "❌ Неизвестная команда. Введите 'помощь' для списка команд."))))
    
    (catch Exception e
      (str "⚠️  Ошибка выполнения команды: " (.getMessage e) 
           " (тип ошибки: " (class e) ")"))))