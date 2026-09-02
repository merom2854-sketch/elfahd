import { readFile, writeFile } from "node:fs/promises";

const healthPath = "data/source-health.json";
const noticesPath = "data/notifications.json";
const checks = [{
  id: "akwam-catalog",
  name: "الكتالوج الأساسي",
  url: "https://akwam-stream-fetcher.meroo3292.workers.dev/?action=genre&genre=https%3A%2F%2Fakwam.it%2Fseries",
}];

const readJson = async (path) => JSON.parse(await readFile(path, "utf8"));
const writeJson = async (path, value) => writeFile(path, `${JSON.stringify(value, null, 2)}\n`, "utf8");

async function checkSource(source) {
  const controller = new AbortController();
  const timeout = setTimeout(() => controller.abort(), 12_000);
  try {
    const response = await fetch(source.url, {
      headers: { Accept: "application/json", "User-Agent": "AlFahdTV-SourceMonitor/1.0" },
      signal: controller.signal,
    });
    const body = await response.text();
    return response.ok && body.includes('"status":"success"') ? "up" : "down";
  } catch {
    return "down";
  } finally {
    clearTimeout(timeout);
  }
}

const health = await readJson(healthPath);
const notices = await readJson(noticesPath);
let changed = false;
for (const source of checks) {
  const next = await checkSource(source);
  const current = health.sources.find((item) => item.id === source.id) || { id: source.id, name: source.name, status: "unknown" };
  if (current.status !== next) {
    const previous = current.status;
    const updated = {
      ...current,
      name: source.name,
      status: next,
      lastChangedAt: new Date().toISOString(),
      message: next === "up" ? "المصدر عاد للعمل" : "المصدر لا يستجيب الآن",
    };
    health.sources = health.sources.filter((item) => item.id !== source.id);
    health.sources.push(updated);
    health.updatedAt = updated.lastChangedAt;
    changed = true;
    if (previous === "up" && next === "down") {
      notices.data = {
        id: crypto.randomUUID(),
        title: "تنبيه من الفهد TV",
        body: `تعذر الوصول إلى ${source.name} مؤقتًا، ويجري العمل على المصدر الاحتياطي.`,
        deepLink: "",
        createdAt: updated.lastChangedAt,
      };
    }
  }
}
if (changed) {
  await writeJson(healthPath, health);
  await writeJson(noticesPath, notices);
}
