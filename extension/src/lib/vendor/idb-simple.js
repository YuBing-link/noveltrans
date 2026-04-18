// IndexedDB 封装 - 简化版 idb
// 提供简单的 Promise API 来操作 IndexedDB

class IDB {
  constructor(dbName, version, upgradeCallback) {
    this.dbName = dbName;
    this.version = version;
    this.upgradeCallback = upgradeCallback;
    this.db = null;
  }

  async open() {
    if (this.db) {
      return this.db;
    }

    return new Promise((resolve, reject) => {
      const request = indexedDB.open(this.dbName, this.version);

      request.onupgradeneeded = (event) => {
        const db = event.target.result;
        if (this.upgradeCallback) {
          this.upgradeCallback(db, event);
        }
      };

      request.onsuccess = (event) => {
        this.db = event.target.result;
        resolve(this.db);
      };

      request.onerror = (event) => {
        reject(event.target.error);
      };
    });
  }

  async close() {
    if (this.db) {
      this.db.close();
      this.db = null;
    }
  }

  async get(storeName, key) {
    const db = await this.open();
    return new Promise((resolve, reject) => {
      const transaction = db.transaction(storeName, 'readonly');
      const store = transaction.objectStore(storeName);
      const request = store.get(key);

      request.onsuccess = () => resolve(request.result);
      request.onerror = () => reject(request.error);
    });
  }

  async set(storeName, key, value) {
    const db = await this.open();
    return new Promise((resolve, reject) => {
      const transaction = db.transaction(storeName, 'readwrite');
      const store = transaction.objectStore(storeName);
      const request = store.put({ key, value, timestamp: Date.now() });

      request.onsuccess = () => resolve();
      request.onerror = () => reject(request.error);
    });
  }

  async delete(storeName, key) {
    const db = await this.open();
    return new Promise((resolve, reject) => {
      const transaction = db.transaction(storeName, 'readwrite');
      const store = transaction.objectStore(storeName);
      const request = store.delete(key);

      request.onsuccess = () => resolve();
      request.onerror = () => reject(request.error);
    });
  }

  async clear(storeName) {
    const db = await this.open();
    return new Promise((resolve, reject) => {
      const transaction = db.transaction(storeName, 'readwrite');
      const store = transaction.objectStore(storeName);
      const request = store.clear();

      request.onsuccess = () => resolve();
      request.onerror = () => reject(request.error);
    });
  }

  async keys(storeName) {
    const db = await this.open();
    return new Promise((resolve, reject) => {
      const transaction = db.transaction(storeName, 'readonly');
      const store = transaction.objectStore(storeName);
      const request = store.getAllKeys();

      request.onsuccess = () => resolve(request.result);
      request.onerror = () => reject(request.error);
    });
  }

  async getAll(storeName) {
    const db = await this.open();
    return new Promise((resolve, reject) => {
      const transaction = db.transaction(storeName, 'readonly');
      const store = transaction.objectStore(storeName);
      const request = store.getAll();

      request.onsuccess = () => resolve(request.result);
      request.onerror = () => reject(request.error);
    });
  }

  async deleteExpired(storeName, maxAge) {
    const db = await this.open();
    const now = Date.now();
    const records = await this.getAll(storeName);

    return Promise.all(
      records
        .filter(record => now - record.timestamp > maxAge)
        .map(record => this.delete(storeName, record.key))
    );
  }
}

// 创建翻译缓存数据库
const createTranslationCacheDB = () => {
  const dbName = 'translationCacheDB';
  const version = 1;

  const upgradeCallback = (db, event) => {
    if (!db.objectStoreNames.contains('translations')) {
      const store = db.createObjectStore('translations', { keyPath: 'key' });
      store.createIndex('timestamp', 'timestamp', { unique: false });
    }
  };

  return new IDB(dbName, version, upgradeCallback);
};

self.createTranslationCacheDB = createTranslationCacheDB;
