(ns game.core
  "Главная точка входа и интеграции всех систем игры.
   Связывает STM модули, команды и сетевую часть."
  (:require [game.game.world :as world]
            [game.game.items :as items]
            [game.players.state :as players]
            [game.players.inventory :as inventory]
            [game.util.helpers :as helpers]
            ;; Импорт оригинальных модулей Mire
            [mire.player :as mire-player]
            [mire.rooms :as mire-rooms]
            [mire.commands :as mire-commands]
            [mire.util :as mire-util]))

;; ГЛОБАЛЬНОЕ СОСТОЯНИЕ ИГРЫ (интеграция всех систем)
(defonce game-state
  (ref {
    :initialized false
    :running false
    :start-time nil
    :active-connections 0
    :game-mode :cooperative
    :difficulty :normal
    :max-players 4
    :current-cycle 0
  }))

;; ИНИЦИАЛИЗАЦИЯ ВСЕХ СИСТЕМ
(defn init-all-systems
  "Инициализировать все системы игры"
  []
  (dosync
    ;; Инициализируем базовые системы
    (println "=== ИНИЦИАЛИЗАЦИЯ СИСТЕМ ИГРЫ ===")
    
    ;; 1. Загружаем оригинальный Mire мир
    (println "[1/5] Загрузка оригинального Mire...")
    (mire-rooms/add-rooms)
    
    ;; 2. Инициализируем наш STM мир
    (println "[2/5] Инициализация STM мира...")
    ;; (мир уже инициализирован при загрузке world.clj)
    
    ;; 3. Инициализируем систему предметов
    (println "[3/5] Инициализация системы предметов...")
    ;; (предметы уже инициализированы)
    
    ;; 4. Инициализируем системы игроков
    (println "[4/5] Инициализация систем игроков...")
    ;; (системы уже инициализированы)
    
    ;; 5. Настраиваем интеграцию
    (println "[5/5] Настройка интеграции...")
    
    ;; Устанавливаем флаги
    (alter game-state assoc 
           :initialized true
           :start-time (System/currentTimeMillis)
           :running true)
    
    (println "=== ВСЕ СИСТЕМЫ ИНИЦИАЛИЗИРОВАНЫ ===\n")))

;; ИНТЕГРАЦИЯ С ОРИГИНАЛЬНЫМ MIRE
(defn integrate-mire-player
  "Интегрировать оригинальную систему игроков Mire с нашей STM"
  [player-name]
  (dosync
    ;; Создаем игрока в оригинальной системе Mire
    (mire-player/add-player player-name)
    
    ;; Создаем игрока в нашей STM системе
    (players/connect-player! player-name)
    
    ;; Синхронизируем позицию
    (let [mire-room (mire-player/current-room player-name)
          our-room :start]  ;; По умолчанию стартовая комната
      
      ;; Если в Mire есть комната, конвертируем ее
      (when mire-room
        ;; Здесь нужно преобразовать имя комнаты Mire в наше
        ;; Пока используем стартовую
        ))
    
    ;; Инициализируем инвентарь
    (inventory/init-player-inventory player-name)
    
    (println "[INTEGRATION] Игрок" player-name "интегрирован")))

(defn convert-mire-room
  "Конвертировать комнату Mire в нашу структуру"
  [mire-room-name]
  ;; Базовая конвертация - по умолчанию используем :start
  ;; Можно расширить для полной конвертации всех комнат
  :start)

(defn sync-player-movement
  "Синхронизировать перемещение игрока между системами"
  [player-name direction]
  (dosync
    (let [current-room (world/get-player-room player-name)
          target-room (world/get-exit-room current-room (helpers/get-direction-keyword direction))]
      
      (when target-room
        ;; Перемещаем в нашей системе
        (world/move-player-between-rooms! current-room target-room player-name)
        (world/set-player-room! player-name target-room)
        
        ;; Обновляем статистику
        (players/add-visited-room! player-name target-room)
        
        ;; Синхронизируем с Mire (если нужно)
        ;; (mire-player/move-player player-name direction)
        
        target-room))))

;; ОБРАБОТКА КОМАНД
(defn handle-command
  "Обработать команду игрока (интеграция с Mire и наша логика)"
  [player-name input]
  (dosync
    (try
      ;; Обновляем время последнего действия
      (players/update-last-action! player-name)
      
      ;; Парсим команду
      (let [parsed (helpers/parse-command input)
            command (:command parsed)
            normalized (helpers/normalize-command command)
            args (:args parsed)]
        
        ;; Обрабатываем команду
        (case normalized
          
          ;; Базовые команды Mire
          "look" (handle-look player-name)
          "go" (handle-go player-name args)
          "north" (handle-move player-name "север")
          "south" (handle-move player-name "юг")
          "west" (handle-move player-name "запад")
          "east" (handle-move player-name "восток")
          "inventory" (handle-inventory player-name)
          
          ;; Наши расширенные команды
          "взять" (handle-take player-name args)
          "положить" (handle-drop player-name args)
          "осмотреть" (handle-examine player-name args)
          "использовать" (handle-use player-name args)
          "сказать" (handle-say player-name args)
          "помощь" (handle-help player-name)
          "статус" (handle-status player-name)
          
          ;; Дефолтная обработка через Mire
          (handle-mire-command player-name input)))
      
      ;; Увеличиваем счетчик команд
      (players/increment-stat! player-name :commands-executed)
      
      (catch Exception e
        (println "[ERROR]" player-name "ошибка в команде:" input e)
        {:error true
         :message "Ошибка выполнения команды"}))))

(defn handle-look
  "Обработать команду look"
  [player-name]
  (let [room (world/get-player-room player-name)
        room-name (world/get-room-name room)
        room-desc (world/get-room-desc room)
        room-items (world/get-room-items room)
        room-players (world/get-room-players room)
        exits (world/get-available-exits room)]
    
    {:type :look
     :room room-name
     :description room-desc
     :items (helpers/format-item-list room-items)
     :players (helpers/format-player-list (disj room-players player-name))
     :exits (helpers/format-exits room)}))

(defn handle-move
  "Обработать перемещение"
  [player-name direction]
  (let [result (sync-player-movement player-name direction)]
    (if result
      (do
        (handle-look player-name))  ;; Показываем новую комнату
      {:error true
       :message "Нельзя пойти в этом направлении"})))

(defn handle-go
  "Обработать команду go [направление]"
  [player-name args]
  (if (empty? args)
    {:error true
     :message "Укажите направление: go [север|юг|запад|восток]"}
    (handle-move player-name (first args))))

(defn handle-inventory
  "Показать инвентарь"
  [player-name]
  (let [inv (inventory/list-inventory player-name)]
    {:type :inventory
     :player player-name
     :items (:items inv)
     :total (:total-items inv)
     :weight (:current-weight inv)
     :max-weight (:max-weight inv)
     :capacity (:capacity-percent inv)}))

(defn handle-take
  "Взять предмет"
  [player-name args]
  (if (empty? args)
    {:error true
     :message "Укажите предмет: взять [предмет]"}
    (let [item-pattern (helpers/unwords args)
          room (world/get-player-room player-name)
          item-name (helpers/find-item-in-room room item-pattern)]
      
      (if item-name
        (if (inventory/add-to-inventory! player-name item-name)
          {:success true
           :message (str "Вы взяли: " (items/get-item-name item-name))
           :item item-name}
          {:error true
           :message "Не удалось взять предмет"})
        {:error true
         :message "Такого предмета здесь нет"}))))

(defn handle-drop
  "Положить предмет"
  [player-name args]
  (if (empty? args)
    {:error true
     :message "Укажите предмет: положить [предмет]"}
    (let [item-pattern (helpers/unwords args)
          item-name (helpers/find-item-in-inventory player-name item-pattern)]
      
      (if item-name
        (if (inventory/remove-from-inventory! player-name item-name)
          {:success true
           :message (str "Вы положили: " (items/get-item-name item-name))
           :item item-name}
          {:error true
           :message "Не удалось положить предмет"})
        {:error true
         :message "У вас нет такого предмета"}))))

(defn handle-examine
  "Осмотреть предмет"
  [player-name args]
  (if (empty? args)
    {:error true
     :message "Укажите предмет или 'комната' для осмотра комнаты"}
    (let [target (helpers/unwords args)]
      (if (= target "комната")
        (handle-look player-name)
        ;; Ищем предмет в инвентаре или комнате
        (let [inv-item (helpers/find-item-in-inventory player-name target)
              room-item (helpers/find-item-in-room (world/get-player-room player-name) target)
              item-name (or inv-item room-item)]
          
          (if item-name
            (let [exam-result (items/examine-item! item-name player-name)]
              {:type :examine
               :item item-name
               :display-name (items/get-item-name item-name)
               :examination (:examination exam-result)
               :readable (:readable exam-result)})
            {:error true
             :message "Нечего осматривать"}))))))

(defn handle-use
  "Использовать предмет"
  [player-name args]
  (if (empty? args)
    {:error true
     :message "Укажите предмет: использовать [предмет]"}
    (let [item-pattern (helpers/unwords args)
          item-name (helpers/find-item-in-inventory player-name item-pattern)]
      
      (if item-name
        (let [result (inventory/use-item-from-inventory! player-name item-name)]
          (if (:success result)
            {:success true
             :message (:message result)
             :item item-name}
            {:error true
             :message (:message result)}))
        {:error true
         :message "У вас нет такого предмета"}))))

(defn handle-say
  "Сказать что-то в комнате"
  [player-name args]
  (if (empty? args)
    {:error true
     :message "Скажите что-нибудь: сказать [текст]"}
    (let [message (helpers/unwords args)
          room (world/get-player-room player-name)
          other-players (helpers/get-players-in-same-room player-name)]
      
      {:type :say
       :player player-name
       :message message
       :room room
       :to-players other-players})))

(defn handle-help
  "Показать помощь"
  [player-name]
  {:type :help
   :commands [
     "север, юг, запад, восток (или с, ю, з, в) - перемещение"
     "look (осмотреть) - осмотреть комнату"
     "взять [предмет] - взять предмет"
     "положить [предмет] - положить предмет"
     "инвентарь - показать инвентарь"
     "осмотреть [предмет] - осмотреть предмет"
     "осмотреть комната - осмотреть комнату"
     "использовать [предмет] - использовать предмет"
     "сказать [текст] - сказать в комнате"
     "помощь - эта справка"
     "статус - ваш статус"
   ]})

(defn handle-status
  "Показать статус игрока"
  [player-name]
  (let [stats (players/get-player-stats player-name)
        play-time (players/get-player-play-time player-name)
        room (world/get-player-room player-name)
        inv-size (inventory/get-inventory-size player-name)]
    
    {:type :status
     :player player-name
     :room (world/get-room-name room)
     :play-time (format "%.1f" play-time)
     :commands (:commands-executed stats 0)
     :items-taken (:items-taken stats 0)
     :rooms-visited (count (:rooms-visited stats))
     :inventory-size inv-size}))

(defn handle-mire-command
  "Обработать команду через оригинальный Mire"
  [player-name input]
  ;; Пробуем выполнить через оригинальную систему Mire
  (try
    (let [result (mire-commands/execute-command player-name input)]
      {:type :mire
       :result result})
    (catch Exception e
      {:error true
       :message "Неизвестная команда. Введите 'помощь' для списка команд."})))

;; УПРАВЛЕНИЕ ИГРОЙ
(defn start-game
  "Запустить игру"
  []
  (dosync
    (when-not (:running @game-state)
      (init-all-systems)
      (alter game-state assoc :running true)
      (println "[GAME] Игра запущена"))
    true))

(defn stop-game
  "Остановить игру"
  []
  (dosync
    (when (:running @game-state)
      (println "[GAME] Остановка игры...")
      
      ;; Отключаем всех игроков
      (doseq [player-name (world/get-all-players)]
        (players/disconnect-player! player-name))
      
      (alter game-state assoc :running false)
      (println "[GAME] Игра остановлена"))
    true))

(defn reset-game
  "Сбросить игру к начальному состоянию"
  []
  (dosync
    (println "[GAME] Сброс игры...")
    (stop-game)
    
    ;; Сбрасываем мир
    (world/reset-world!)
    
    ;; Сбрасываем статистику игроков
    (players/reset-all-stats!)
    
    ;; Сбрасываем инвентари
    ;; (нужно добавить функцию сброса инвентарей)
    
    (println "[GAME] Игра сброшена")
    true))

(defn get-game-status
  "Получить статус игры"
  []
  (let [state @game-state
        players-count (count (world/get-all-players))]
    
    {:running (:running state)
     :uptime (if (:start-time state)
               (helpers/elapsed-time (:start-time state))
               0)
     :players players-count
     :max-players (:max-players state)
     :mode (:game-mode state)
     :difficulty (:difficulty state)
     :cycle (:current-cycle state)}))

;; ТОЧКА ВХОДА
(defn -main
  "Главная функция для запуска игры"
  [& args]
  (println "Запуск игры...")
  (println "STM кооперативная игра 'Побег из лаборатории'")
  (println "==============================================")
  
  (start-game)
  
  (println "\nИгра готова к подключению игроков.")
  (println "Для выхода нажмите Ctrl+C")
  
  ;; Для тестирования в REPL
  (when (some #{"--test"} args)
    (println "\n=== ТЕСТОВЫЙ РЕЖИМ ===")
    (integrate-mire-player "ТестовыйИгрок")
    
    ;; Пример тестовых команд
    (println (handle-command "ТестовыйИгрок" "look"))
    (println (handle-command "ТестовыйИгрок" "взять ключ"))
    (println (handle-command "ТестовыйИгрок" "инвентарь")))
  
  ;; Держим приложение запущенным
  (while (:running @game-state)
    (Thread/sleep 1000)
    (dosync
      (alter game-state update :current-cycle inc))))

;; ЭКСПОРТ
(defn init-core
  "Инициализировать ядро игры"
  []
  (println "[CORE] Ядро игры инициализировано"))

(init-core)

;; Для работы с REPL
(comment
  ;; Запустить игру
  (start-game)
  
  ;; Добавить тестового игрока
  (integrate-mire-player "Алексей")
  
  ;; Выполнить команды
  (handle-command "Алексей" "look")
  (handle-command "Алексей" "взять ключ-карта")
  (handle-command "Алексей" "инвентарь")
  
  ;; Проверить статус игры
  (get-game-status)
  
  ;; Остановить игру
  (stop-game)
)
