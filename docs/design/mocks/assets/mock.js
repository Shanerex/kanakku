// Mock chrome: a top bar with a theme toggle and a link back to the gallery.
// Not part of the design; the app's theme switch lives in Me > Appearance.
(() => {
  const bar = document.createElement("div");
  bar.className = "mock-bar";
  bar.innerHTML =
    '<a href="index.html">&larr; Kanakku mocks</a><span class="sp"></span>' +
    "<span>theme:</span>" +
    '<button data-t="">system</button><button data-t="light">light</button><button data-t="dark">dark</button>';
  document.body.prepend(bar);
  const apply = (t) => {
    if (t) document.documentElement.dataset.theme = t;
    else delete document.documentElement.dataset.theme;
    try { localStorage.setItem("kanakku-mock-theme", t); } catch {}
  };
  bar.addEventListener("click", (e) => {
    const t = e.target.closest("button")?.dataset.t;
    if (t !== undefined) apply(t);
  });
  let saved = "";
  try { saved = localStorage.getItem("kanakku-mock-theme") || ""; } catch {}
  const q = new URLSearchParams(location.search).get("theme");
  apply(q ?? saved);
})();
