-- Инициализация расширения pgvector
CREATE EXTENSION IF NOT EXISTS vector;

-- Таблица для хранения эмбеддингов событий игры
CREATE TABLE IF NOT EXISTS event_embeddings (
    id BIGSERIAL PRIMARY KEY,
    event_id BIGINT NOT NULL,
    campaign_id BIGINT NOT NULL,  -- Ссылка на campaigns.id
    embedding vector(1024) NOT NULL,  -- BGE-M3 возвращает векторы размерностью 1024
    description TEXT NOT NULL,
    quest_context TEXT,
    location_context TEXT,
    npc_context TEXT,
    event_type VARCHAR(100),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    
    -- Индексы для быстрого поиска
    CONSTRAINT fk_event FOREIGN KEY (event_id) REFERENCES game_events(id) ON DELETE CASCADE,
    CONSTRAINT fk_campaign FOREIGN KEY (campaign_id) REFERENCES campaigns(id) ON DELETE CASCADE
);

-- Индекс для векторного поиска (IVFFlat для быстрого поиска)
CREATE INDEX IF NOT EXISTS event_embeddings_vector_idx 
ON event_embeddings 
USING ivfflat (embedding vector_cosine_ops) 
WITH (lists = 100);

-- Индексы для фильтрации
CREATE INDEX IF NOT EXISTS event_embeddings_campaign_idx ON event_embeddings(campaign_id);
CREATE INDEX IF NOT EXISTS event_embeddings_event_id_idx ON event_embeddings(event_id);
CREATE INDEX IF NOT EXISTS event_embeddings_type_idx ON event_embeddings(event_type);
CREATE INDEX IF NOT EXISTS event_embeddings_created_at_idx ON event_embeddings(created_at DESC);

-- Функция для автоматического обновления updated_at
CREATE OR REPLACE FUNCTION update_updated_at_column()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = CURRENT_TIMESTAMP;
    RETURN NEW;
END;
$$ language 'plpgsql';

-- Триггер для автоматического обновления updated_at
CREATE TRIGGER update_event_embeddings_updated_at 
BEFORE UPDATE ON event_embeddings 
FOR EACH ROW 
EXECUTE FUNCTION update_updated_at_column();

-- Комментарии для документации
COMMENT ON TABLE event_embeddings IS 'Таблица для хранения векторных представлений событий игры для семантического поиска';
COMMENT ON COLUMN event_embeddings.embedding IS 'Векторное представление события, полученное через BGE-M3 (размерность 1024)';
COMMENT ON COLUMN event_embeddings.quest_context IS 'Контекст квеста для улучшения релевантности поиска';
COMMENT ON COLUMN event_embeddings.location_context IS 'Контекст локации для фильтрации';
COMMENT ON COLUMN event_embeddings.npc_context IS 'Контекст NPC для фильтрации';
