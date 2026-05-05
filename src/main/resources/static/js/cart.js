/* ═══════════════════════════════════════════════════════════════
   cart.js — Cart state, persistence, UI rendering
═══════════════════════════════════════════════════════════════ */

const Cart = (() => {
    let items = [];
    let restaurantId   = null;
    let restaurantName = '';

    function load() {
        try {
            const saved = JSON.parse(sessionStorage.getItem('cart') || '{}');
            items          = saved.items          || [];
            restaurantId   = saved.restaurantId   || null;
            restaurantName = saved.restaurantName || '';
        } catch { items = []; }
    }

    function save() {
        sessionStorage.setItem('cart', JSON.stringify({ items, restaurantId, restaurantName }));
    }

    function setRestaurant(id, name) {
        if (restaurantId && restaurantId !== id && items.length > 0) {
            if (!confirm(`Your cart has items from "${restaurantName}". Clear cart and switch to "${name}"?`)) {
                return false;
            }
            items = [];
        }
        restaurantId   = id;
        restaurantName = name;
        save();
        return true;
    }

    function addItem(menuItemId, name, price) {
        const existing = items.find(i => i.menuItemId === menuItemId);
        if (existing) existing.quantity++;
        else items.push({ menuItemId, name, price, quantity: 1 });
        save();
        renderAll();
    }

    function removeItem(menuItemId) {
        const idx = items.findIndex(i => i.menuItemId === menuItemId);
        if (idx === -1) return;
        if (items[idx].quantity > 1) items[idx].quantity--;
        else items.splice(idx, 1);
        save();
        renderAll();
    }

    function deleteItem(menuItemId) {
        items = items.filter(i => i.menuItemId !== menuItemId);
        save();
        renderAll();
    }

    function clear() { items = []; restaurantId = null; restaurantName = ''; save(); renderAll(); }

    function getCount()  { return items.reduce((s, i) => s + i.quantity, 0); }
    function getTotal()  { return items.reduce((s, i) => s + i.price * i.quantity, 0); }
    function getItems()  { return [...items]; }
    function getRestaurantId() { return restaurantId; }

    // ── Render cart panel ─────────────────────────────────────
    function renderAll() {
        renderCartItems();
        renderCartSummary();
        renderFloatingBtn();
        renderQtyButtons();
    }

    function renderCartItems() {
        const el = document.getElementById('cartItemsList');
        if (!el) return;

        if (items.length === 0) {
            el.innerHTML = `
                <div class="cart-empty">
                    <div class="cart-empty-icon">🛒</div>
                    <p>Your cart is empty</p>
                    <p style="font-size:0.8rem;margin-top:0.3rem">Add items from the menu</p>
                </div>`;
            return;
        }

        el.innerHTML = items.map(i => `
            <div class="cart-item-row">
                <div style="flex:1">
                    <div class="cart-item-name">${i.name}</div>
                    <div class="cart-item-qty">${i.quantity} × ${formatPrice(i.price)}</div>
                </div>
                <div class="cart-item-price">${formatPrice(i.price * i.quantity)}</div>
                <button class="cart-item-remove" onclick="Cart.deleteItem(${i.menuItemId})" title="Remove">✕</button>
            </div>`).join('');
    }

    function renderCartSummary() {
        const totalEl    = document.getElementById('cartTotal');
        const subtotalEl = document.getElementById('cartSubtotal');
        const countEl    = document.getElementById('cartCountBadge');
        const total      = getTotal();
        const delivery   = total > 0 ? 2.99 : 0;

        if (subtotalEl) subtotalEl.textContent = formatPrice(total);
        if (totalEl)    totalEl.textContent    = formatPrice(total + delivery);
        if (countEl)    countEl.textContent    = getCount();
    }

    function renderFloatingBtn() {
        const btn = document.getElementById('floatingCartBtn');
        if (!btn) return;
        const count = getCount();
        btn.style.display = count > 0 ? 'flex' : 'none';
        const span = btn.querySelector('.floating-cart-count');
        if (span) span.textContent = count;
        const priceSpan = btn.querySelector('.floating-cart-price');
        if (priceSpan) priceSpan.textContent = formatPrice(getTotal());
    }

    function renderQtyButtons() {
        items.forEach(item => {
            const qtyEl = document.getElementById(`qty-${item.menuItemId}`);
            if (qtyEl) qtyEl.textContent = item.quantity;

            const addBtn = document.getElementById(`add-${item.menuItemId}`);
            const subBtn = document.getElementById(`sub-${item.menuItemId}`);
            if (addBtn) addBtn.style.display = 'flex';
            if (subBtn) subBtn.style.display = item.quantity > 0 ? 'flex' : 'none';
        });
    }

    load();
    return { load, save, setRestaurant, addItem, removeItem, deleteItem, clear,
             getCount, getTotal, getItems, getRestaurantId, renderAll };
})();
