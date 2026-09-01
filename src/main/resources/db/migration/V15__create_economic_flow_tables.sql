CREATE TABLE topics (
  id BIGSERIAL PRIMARY KEY,
  topic_key VARCHAR(64) NOT NULL UNIQUE,
  name VARCHAR(128) NOT NULL,
  domain VARCHAR(32) NOT NULL,
  active BOOLEAN NOT NULL DEFAULT TRUE,
  aliases TEXT,
  created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE topic_candidates (
  id BIGSERIAL PRIMARY KEY,
  name VARCHAR(128) NOT NULL,
  article_id VARCHAR(64) NOT NULL REFERENCES articles(id),
  status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
  created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  UNIQUE (name, article_id)
);

CREATE TABLE economic_events (
  id BIGSERIAL PRIMARY KEY,
  event_type VARCHAR(40) NOT NULL,
  title TEXT NOT NULL,
  subject TEXT NOT NULL,
  subject_key VARCHAR(128) NOT NULL,
  event_date DATE NOT NULL,
  status VARCHAR(32) NOT NULL,
  previous_value TEXT,
  previous_value_normalized TEXT,
  new_value TEXT,
  new_value_normalized TEXT,
  value_unit VARCHAR(16),
  region_code VARCHAR(128),
  dedup_key VARCHAR(64) UNIQUE,
  created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_economic_events_lookup
  ON economic_events (event_type, event_date, subject_key);

CREATE TABLE event_topics (
  event_id BIGINT NOT NULL REFERENCES economic_events(id) ON DELETE CASCADE,
  topic_id BIGINT NOT NULL REFERENCES topics(id),
  PRIMARY KEY (event_id, topic_id)
);

CREATE TABLE event_evidence (
  id BIGSERIAL PRIMARY KEY,
  event_id BIGINT NOT NULL REFERENCES economic_events(id) ON DELETE CASCADE,
  article_id VARCHAR(64) NOT NULL REFERENCES articles(id),
  evidence_text TEXT NOT NULL,
  source_type VARCHAR(32) NOT NULL DEFAULT 'ARTICLE',
  created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  UNIQUE (event_id, article_id)
);

CREATE TABLE event_relations (
  id BIGSERIAL PRIMARY KEY,
  from_event_id BIGINT NOT NULL REFERENCES economic_events(id) ON DELETE CASCADE,
  to_event_id BIGINT NOT NULL REFERENCES economic_events(id) ON DELETE CASCADE,
  relation_type VARCHAR(32) NOT NULL,
  confidence DOUBLE PRECISION NOT NULL,
  evidence_article_id VARCHAR(64) REFERENCES articles(id),
  created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  UNIQUE (from_event_id, to_event_id, relation_type),
  CHECK (from_event_id <> to_event_id)
);

INSERT INTO topics (topic_key, name, domain, aliases) VALUES
('MORTGAGE','주택담보대출','REAL_ESTATE','주담대,mortgage'),
('HOUSEHOLD_DEBT','가계부채','REAL_ESTATE',NULL),
('HOUSING_PRICE','주택가격','REAL_ESTATE','집값'),
('HOUSING_SUPPLY','주택공급','REAL_ESTATE',NULL),
('JEONSE','전세','REAL_ESTATE',NULL),
('HOUSING_TRANSACTION','주택거래','REAL_ESTATE',NULL),
('UNSOLD_HOUSING','미분양','REAL_ESTATE',NULL),
('DSR','총부채원리금상환비율','REAL_ESTATE','DSR'),
('LTV','주택담보인정비율','REAL_ESTATE','LTV'),
('REDEVELOPMENT','재개발','REAL_ESTATE','재건축'),
('HOUSING_TAX','부동산세제','FISCAL','종부세,재산세'),
('BASE_RATE','기준금리','MONETARY',NULL),
('MARKET_RATE','시장금리','MONETARY','국고채 금리'),
('LOAN_RATE','대출금리','FINANCE',NULL),
('DEPOSIT_RATE','예금금리','FINANCE',NULL),
('LIQUIDITY','유동성','MONETARY',NULL),
('CORPORATE_LOAN','기업대출','FINANCE',NULL),
('CPI','소비자물가','PRICE','CPI'),
('PPI','생산자물가','PRICE','PPI'),
('IMPORT_PRICE','수입물가','PRICE',NULL),
('OIL_PRICE','국제유가','PRICE','유가,WTI,브렌트유'),
('EXCHANGE_RATE','환율','FX',NULL),
('USD_KRW','원달러 환율','FX','달러-원,원·달러'),
('JPY_KRW','원엔 환율','FX','엔-원,원·엔'),
('FX_RESERVE','외환보유액','FX',NULL),
('KOSPI','코스피','STOCK','KOSPI'),
('KOSDAQ','코스닥','STOCK','KOSDAQ'),
('SHORT_SELLING','공매도','STOCK',NULL),
('FOREIGN_INVESTOR','외국인투자자','STOCK','외국인 순매수,외국인 순매도'),
('CAPITAL_MARKET','자본시장','STOCK',NULL),
('GDP','국내총생산','MACRO','GDP'),
('ECONOMIC_GROWTH','경제성장','MACRO','성장률'),
('EMPLOYMENT','고용','EMPLOYMENT','취업자'),
('UNEMPLOYMENT','실업','EMPLOYMENT','실업률'),
('CONSUMPTION','소비','MACRO',NULL),
('EXPORT','수출','TRADE',NULL),
('IMPORT','수입','TRADE',NULL),
('SEMICONDUCTOR','반도체','INDUSTRY','HBM'),
('AUTOMOBILE','자동차','INDUSTRY',NULL),
('BATTERY','배터리','INDUSTRY','이차전지'),
('SHIPBUILDING','조선','INDUSTRY',NULL),
('DEFENSE','방산','INDUSTRY','방위산업');
