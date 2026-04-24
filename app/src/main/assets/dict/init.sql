-- 词库初始化脚本
CREATE TABLE IF NOT EXISTS dict_pinyin (id INTEGER PRIMARY KEY, word TEXT, pinyin TEXT, pinyin_abbr TEXT, frequency INTEGER DEFAULT 0);
CREATE TABLE IF NOT EXISTS stroke_dict (id INTEGER PRIMARY KEY, word TEXT, strokes TEXT, frequency INTEGER DEFAULT 0);
CREATE TABLE IF NOT EXISTS user_dict (id INTEGER PRIMARY KEY, word TEXT UNIQUE, pinyin TEXT, frequency INTEGER DEFAULT 1, last_used INTEGER);
CREATE INDEX IF NOT EXISTS idx_pinyin ON dict_pinyin(pinyin);
CREATE INDEX IF NOT EXISTS idx_abbr ON dict_pinyin(pinyin_abbr);
CREATE INDEX IF NOT EXISTS idx_strokes ON stroke_dict(strokes);
INSERT OR IGNORE INTO dict_pinyin (word, pinyin, pinyin_abbr, frequency) VALUES
('的', 'de', 'd', 100000), ('是', 'shi', 's', 95000), ('了', 'le', 'l', 90000),
('在', 'zai', 'z', 85000), ('我', 'wo', 'w', 80000), ('有', 'you', 'y', 75000),
('和', 'he', 'h', 70000), ('就', 'jiu', 'j', 65000), ('不', 'bu', 'b', 60000),
('人', 'ren', 'r', 55000), ('都', 'dou', 'd', 50000), ('一', 'yi', 'y', 48000),
('个', 'ge', 'g', 46000), ('上', 'shang', 's', 44000), ('也', 'ye', 'y', 42000),
('很', 'hen', 'h', 40000), ('到', 'dao', 'd', 38000), ('说', 'shuo', 's', 36000),
('要', 'yao', 'y', 35000), ('去', 'qu', 'q', 34000), ('你', 'ni', 'n', 33000),
('会', 'hui', 'h', 32000), ('着', 'zhe', 'z', 31000), ('没有', 'mei you', 'my', 30000),
('看', 'kan', 'k', 29000), ('好', 'hao', 'h', 28000), ('自己', 'zi ji', 'zj', 27000),
('这', 'zhe', 'z', 26000);
