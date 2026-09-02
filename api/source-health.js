const checks = {
  "akwam-catalog": {
    name: "الكتالوج الأساسي",
    url: "https://akwam-stream-fetcher.meroo3292.workers.dev/?action=genre&genre=https%3A%2F%2Fakwam.it%2Fseries",
  },
};

module.exports = async (request, response) => {
  const id = String(request.query.source || "akwam-catalog");
  const check = checks[id];
  if (!check) return response.status(400).json({ status: "error", message: "مصدر غير معروف" });

  const startedAt = Date.now();
  const controller = new AbortController();
  const timeout = setTimeout(() => controller.abort(), 12_000);
  try {
    const upstream = await fetch(check.url, {
      headers: { Accept: "application/json", "User-Agent": "AlFahdTV-SourceMonitor/1.0" },
      signal: controller.signal,
    });
    const text = await upstream.text();
    const validPayload = upstream.ok && text.includes('"status":"success"');
    return response.status(validPayload ? 200 : 502).json({
      id,
      name: check.name,
      status: validPayload ? "up" : "down",
      checkedAt: new Date().toISOString(),
      latencyMs: Date.now() - startedAt,
      message: validPayload ? "المصدر يستجيب بصورة صحيحة" : `استجابة غير صالحة (${upstream.status})`,
    });
  } catch (error) {
    return response.status(502).json({
      id,
      name: check.name,
      status: "down",
      checkedAt: new Date().toISOString(),
      latencyMs: Date.now() - startedAt,
      message: error && error.name === "AbortError" ? "انتهت مهلة فحص المصدر" : "تعذر الوصول إلى المصدر",
    });
  } finally {
    clearTimeout(timeout);
  }
};
