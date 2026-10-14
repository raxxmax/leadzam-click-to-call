// Sample leads (mock data)
const leads = [
  { id: 1, name: "Rahul Sharma", phone: "+919876543210", stage: "Interested" },
  { id: 2, name: "Priya Verma",  phone: "+918765432109", stage: "Follow-up" },
  { id: 3, name: "Amit Singh",   phone: "+917654321098", stage: "New Lead" },
];

const container = document.getElementById("leads");

// Har lead ke liye ek card banao
leads.forEach((lead) => {
  const card = document.createElement("div");
  card.className = "lead";

  const left = document.createElement("div");
  const nameEl = document.createElement("div");
  nameEl.className = "name";
  nameEl.textContent = lead.name;          // textContent: safe, HTML inject nahi hota
  const phoneEl = document.createElement("div");
  phoneEl.className = "phone";
  phoneEl.textContent = lead.phone;
  const stageEl = document.createElement("span");
  stageEl.className = "stage";
  stageEl.textContent = "Stage: " + lead.stage;
  left.append(nameEl, phoneEl, stageEl);

  const right = document.createElement("div");
  right.className = "right";
  const btn = document.createElement("button");
  btn.textContent = "Call";
  btn.setAttribute("aria-label", "Call " + lead.name);
  const status = document.createElement("div");
  status.className = "status ready";
  status.setAttribute("aria-live", "polite");   // screen reader ke liye
  status.textContent = "Ready";
  right.append(btn, status);

  card.append(left, right);
  container.append(card);

  // Status badalne wala helper
  function setStatus(kind, text) {
    status.className = "status " + kind;
    status.textContent = text;
  }

  btn.addEventListener("click", async () => {
    document.querySelectorAll(".lead").forEach((c) => c.classList.remove("active"));
    card.classList.add("active");           // kaunsa lead call ho raha hai, ye dikhta hai
    btn.disabled = true;
    setStatus("sending", "Sending...");

    try {
      const res = await fetch("/api/dial", {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ phoneNumber: lead.phone, contactName: lead.name }),
      });
      const data = await res.json().catch(() => ({}));

      if (res.ok) {
        setStatus("sent", "Request sent to FCM");   // "Delivered" kabhi nahi
      } else {
        setStatus("error", data.error || "Something went wrong");
      }
    } catch (err) {
      // Server band hai ya network problem
      setStatus("error", "Cannot reach server. Is it running?");
    } finally {
      btn.disabled = false;
      // 5 second baad wapas Ready
      setTimeout(() => {
        setStatus("ready", "Ready");
        card.classList.remove("active");
      }, 5000);
    }
  });
});
