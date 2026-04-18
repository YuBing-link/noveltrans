// p-limit - 简化版
// 限制同时运行的Promise数量

const pLimit = (concurrency) => {
  if (concurrency < 1) {
    throw new TypeError('Expected `concurrency` to be a number from 1 and up');
  }

  const queue = [];
  let activeCount = 0;

  const next = () => {
    activeCount--;

    if (queue.length > 0) {
      queue.shift()();
    }
  };

  const run = async (fn, resolve, reject) => {
    activeCount++;

    const result = (async () => fn())();

    resolve(result);

    try {
      await result;
    } catch {}

    next();
  };

  const enqueue = (fn) => new Promise((resolve, reject) => {
    if (activeCount < concurrency) {
      run(fn, resolve, reject);
      return;
    }

    queue.push(run.bind(null, fn, resolve, reject));
  });

  const generator = (fn, ...args) => {
    if (typeof fn !== 'function') {
      throw new TypeError('Expected function');
    }

    return enqueue(() => fn(...args));
  };

  Object.defineProperties(generator, {
    activeCount: {
      get: () => activeCount
    },
    pendingCount: {
      get: () => queue.length
    },
    clearQueue: {
      value: () => {
        queue.length = 0;
      }
    }
  });

  return generator;
};

// 默认导出
self.pLimit = pLimit;
