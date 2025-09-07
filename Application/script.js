// API endpoints
const API_BASE = 'http://localhost:8080/api/v1';
const ENDPOINTS = {
    login: `${API_BASE}/auth/login`,
    userCoupons: (userId, page, size) => `${API_BASE}/coupons/user/${userId}?page=${page}&size=${size}`,
    processOrder: `${API_BASE}/orders/process`
};

// HTML Sanitization utility - CRITICAL for XSS prevention
function sanitizeHtml(str) {
    if (!str) return '';
    const temp = document.createElement('div');
    temp.textContent = str;
    return temp.innerHTML;
}

function sanitizeAndTruncate(str, maxLength = 200) {
    if (!str) return '';
    const sanitized = sanitizeHtml(str);
    if (sanitized.length > maxLength) {
        return sanitized.substring(0, maxLength) + '...';
    }
    return sanitized;
}

// Global state management
let globalState = {
    auth: {
        token: localStorage.getItem('token') || '',
        userId: localStorage.getItem('userId') || ''
    },
    coupons: {
        data: [],
        currentPage: 0,
        pageSize: 10,
        totalCount: 0,
        loading: false
    },
    ui: {
        activeTab: 'coupons'
    }
};

// Initialize app
document.addEventListener('DOMContentLoaded', function () {
    console.log('App initializing...');

    // Get DOM elements
    const loginSection = document.getElementById('loginSection');
    const appSection = document.getElementById('appSection');
    const loginForm = document.getElementById('loginForm');
    const loginError = document.getElementById('loginError');
    const logoutBtn = document.getElementById('logoutBtn');
    const couponGrid = document.getElementById('couponGrid');
    const couponPagination = document.getElementById('couponPagination');
    const orderForm = document.getElementById('orderForm');
    const orderResult = document.getElementById('orderResult');
    const tabBtns = document.querySelectorAll('.tab-btn');
    const tabContents = document.querySelectorAll('.tab-content');

    // UI Management Functions
    function showLogin() {
        if (loginSection) loginSection.style.display = 'block';
        if (appSection) appSection.style.display = 'none';
    }

    function showApp() {
        if (loginSection) loginSection.style.display = 'none';
        if (appSection) appSection.style.display = 'block';
    }

    function logout() {
        localStorage.removeItem('token');
        localStorage.removeItem('userId');
        globalState.auth.token = '';
        globalState.auth.userId = '';
        globalState.coupons.data = [];
        showLogin();
    }

    // Tab Management
    function switchTab(targetTab) {
        console.log('Switching to tab:', targetTab);

        // Update UI state
        globalState.ui.activeTab = targetTab;

        // Update tab buttons
        tabBtns.forEach(btn => {
            btn.classList.toggle('active', btn.dataset.tab === targetTab);
        });

        // Update tab content
        tabContents.forEach(content => {
            content.classList.toggle('active', content.id === targetTab);
        });

        // Load data based on tab
        if (targetTab === 'coupons' && globalState.auth.token && globalState.auth.userId) {
            // Only reload if we don't have data or it's been a while
            if (globalState.coupons.data.length === 0 || !globalState.coupons.loading) {
                loadUserCoupons();
            } else {
                // Re-render existing data
                renderCoupons();
            }
        }
    }

    // Event Listeners
    if (loginForm) {
        loginForm.addEventListener('submit', async function (e) {
            e.preventDefault();
            if (loginError) loginError.textContent = '';

            const username = document.getElementById('loginUsername')?.value?.trim();
            const password = document.getElementById('loginPassword')?.value;

            if (!username || !password) {
                if (loginError) loginError.textContent = 'Vui lòng nhập tên đăng nhập và mật khẩu';
                return;
            }

            try {
                const res = await fetch(ENDPOINTS.login, {
                    method: 'POST',
                    headers: { 'Content-Type': 'application/json' },
                    body: JSON.stringify({ username, password })
                });

                const data = await res.json();
                console.log('Login response:', data);

                if (data.success && data.token && data.userId) {
                    globalState.auth.token = data.token;
                    globalState.auth.userId = data.userId;
                    localStorage.setItem('token', data.token);
                    localStorage.setItem('userId', data.userId);
                    showApp();
                    // Load coupons after successful login
                    loadUserCoupons();
                } else {
                    if (loginError) loginError.textContent = data.errorMessage || 'Đăng nhập thất bại';
                }
            } catch (err) {
                console.error('Login error:', err);
                if (loginError) loginError.textContent = 'Lỗi kết nối máy chủ';
            }
        });
    }

    if (logoutBtn) {
        logoutBtn.addEventListener('click', logout);
    }

    // Tab switching event listeners
    tabBtns.forEach(btn => {
        btn.addEventListener('click', function () {
            const targetTab = this.dataset.tab;
            switchTab(targetTab);
        });
    });

    // Order form handling
    if (orderForm) {
        orderForm.addEventListener('submit', async function (e) {
            e.preventDefault();
            processOrder();
        });
    }

    const couponTypeRadios = document.querySelectorAll('input[name="couponType"]');
    couponTypeRadios.forEach(radio => {
        radio.addEventListener('change', function() {
            const couponInputGroup = document.getElementById('couponInputGroup');
            if (this.value === 'manual') {
                couponInputGroup.style.display = 'block';
            } else {
                couponInputGroup.style.display = 'none';
                document.getElementById('orderCouponCode').value = '';
                document.getElementById('couponValidation').innerHTML = '';
            }
        });
    });

    const totalAmountInput = document.getElementById('totalAmount');
    if (totalAmountInput) {
        totalAmountInput.addEventListener('input', function(e) {
            e.target.value = e.target.value.replace(/[^\d]/g, '');
        });
    }
    
    async function processOrder() {
        const orderResult = document.getElementById('orderResult');
        if (!orderResult) return;

        // Clear previous results
        orderResult.innerHTML = '<div class="loading-spinner">Đang xử lý đơn hàng...</div>';

        if (!globalState.auth.token || !globalState.auth.userId) {
            orderResult.innerHTML = '<div class="order-error">Bạn cần đăng nhập để sử dụng chức năng này.</div>';
            return;
        }

        const orderAmountStr = document.getElementById('totalAmount')?.value?.replace(/[^\d]/g, '') || '0';
        const orderAmount = parseFloat(orderAmountStr);
        const orderDateInput = document.getElementById('orderDate')?.value;
        const couponCode = document.getElementById('orderCouponCode')?.value?.trim();

        if (orderAmount < 1000) {
            orderResult.innerHTML = '<div class="order-error">Số tiền đơn hàng phải tối thiểu 1.000 VNĐ.</div>';
            return;
        }

        if (!orderDateInput) {
            orderResult.innerHTML = '<div class="order-error">Vui lòng chọn ngày tạo đơn hàng.</div>';
            return;
        }

        try {
            const orderDate = new Date(orderDateInput);
            const year = orderDate.getFullYear();
            const month = String(orderDate.getMonth() + 1).padStart(2, '0');
            const day = String(orderDate.getDate()).padStart(2, '0');
            const hours = String(orderDate.getHours()).padStart(2, '0');
            const minutes = String(orderDate.getMinutes()).padStart(2, '0');
            const seconds = String(orderDate.getSeconds()).padStart(2, '0');
            const orderDateFormatted = `${year}-${month}-${day}T${hours}:${minutes}:${seconds}`;

            // Prepare request body
            const body = {
                userId: Number(globalState.auth.userId),
                orderAmount: orderAmount,
                orderDate: orderDateFormatted
            };

            // Only add couponCode if user entered one
            if (couponCode) {
                body.couponCode = couponCode;
            }

            console.log('Processing order:', body);

            const res = await fetch(ENDPOINTS.processOrder, {
                method: 'POST',
                headers: {
                    'Authorization': 'Bearer ' + globalState.auth.token,
                    'Content-Type': 'application/json'
                },
                body: JSON.stringify(body)
            });

            const data = await res.json();
            console.log('Order response:', data);

            // Check if response is successful (API may not return success field)
            if (res.ok) {
                renderOrderSuccess(data);
            } else {
                const friendlyMessage = getFriendlyErrorMessage(data.code, data.message || 'Có lỗi xảy ra khi xử lý đơn hàng');
                renderOrderError(friendlyMessage);
            }

        } catch (err) {
            console.error('Order processing error:', err);
            renderOrderError('Lỗi kết nối máy chủ. Vui lòng thử lại sau.');
        }
    }

    // Render successful order result - updated for new API response
    function renderOrderSuccess(data) {
        const orderResult = document.getElementById('orderResult');
        if (!orderResult) return;

        const discountAmount = data.discountAmount || 0;
        const finalAmount = data.finalAmount || data.orderAmount;
        const orderAmount = data.orderAmount || 0;
        const savings = discountAmount > 0 ? ((discountAmount / orderAmount) * 100).toFixed(1) : 0;

        orderResult.innerHTML = `
            <div class="order-success">
                <h4>✅ Đơn hàng được xử lý thành công!</h4>

                <div class="order-summary">
                    <div class="summary-row">
                        <span>Tổng tiền đơn hàng:</span>
                        <span><strong>${formatCurrency(orderAmount)}</strong></span>
                    </div>

                    ${discountAmount > 0 ? `
                        <div class="summary-row discount">
                            <span>Giảm giá ${data.couponCode ? `(${data.couponCode})` : ''}:</span>
                            <span class="discount-value">-${formatCurrency(discountAmount)}</span>
                        </div>
                    ` : `
                        <div class="no-discount-info">
                            ℹ️ Không có mã giảm giá được áp dụng
                        </div>
                    `}

                    <div class="summary-row final">
                        <span>Số tiền cần thanh toán:</span>
                        <span class="final-amount">${formatCurrency(finalAmount)}</span>
                    </div>
                </div>

                <div class="order-details">
                    <p><strong>Mã đơn hàng:</strong> #${data.orderId}</p>
                    <p><strong>Trạng thái:</strong> <span class="status-${data.status?.toLowerCase()}">${getOrderStatusText(data.status)}</span></p>
                    <p><strong>Ngày tạo:</strong> ${formatDateTime(data.orderDate)}</p>
                    ${data.couponCode ? `<p><strong>Mã giảm giá:</strong> ${data.couponCode}</p>` : ''}
                </div>

                <div class="order-actions">
                    <button class="btn btn-primary" onclick="createNewOrder()">
                        Tạo đơn hàng mới
                    </button>
                </div>
            </div>
        `;
    }

    // Helper function to get order status text
    function getOrderStatusText(status) {
        const statusMap = {
            'COMPLETED': 'Hoàn thành',
            'PENDING': 'Đang xử lý',
            'CANCELLED': 'Đã hủy',
            'FAILED': 'Thất bại'
        };
        return statusMap[status] || status;
    }

    // Map API error code to friendly message
    function getFriendlyErrorMessage(code, fallbackMessage) {
        if (code === 'INSUFFICIENT_BUDGET') {
            return 'Rất tiếc, coupon này đang tạm hết!';
        } else if (code === 'COUPON_NOT_FOUND') {
            return 'Có vẻ như mã coupon bạn nhập chưa đúng hoặc không còn tồn tại.';
        } else if (code === 'COUPON_EXPIRED') {
            return 'Oops! Coupon này đã hết hạn mất rồi.';
        } else if (code === 'RULE_VIOLATION') {
            return 'Đơn hàng chưa thoả điều kiện, điều chỉnh đơn hàng để tiếp tục sử dụng coupon nhé: ' + fallbackMessage;
        }
        return fallbackMessage || 'Có lỗi xảy ra khi xử lý đơn hàng';
    }

    // Render order error - simplified
    function renderOrderError(message) {
        const orderResult = document.getElementById('orderResult');
        if (!orderResult) return;

        orderResult.innerHTML = `
            <div class="order-error">
                <h4>❌ Không thể xử lý đơn hàng</h4>
                <p>${sanitizeHtml(message)}</p>
                <div class="error-actions">
                    <button class="btn btn-primary" onclick="processOrder()">
                        Thử lại
                    </button>
                    <button class="btn btn-secondary" onclick="clearOrderForm()">
                        Xóa form
                    </button>
                </div>
            </div>
        `;
    }

    // Clear order form - updated
    window.clearOrderForm = function() {
        document.getElementById('totalAmount').value = '';
        document.getElementById('orderDate').value = '';
        document.getElementById('orderCouponCode').value = '';
        document.getElementById('couponValidation').innerHTML = '';

        const orderResult = document.getElementById('orderResult');
        if (orderResult) {
            orderResult.innerHTML = `
                <div class="empty-result">
                    <p>Nhập thông tin đơn hàng và nhấn "Tính toán" để xem kết quả</p>
                </div>
            `;
        }

        // Set default date and time
        setDefaultDateTime();
    };

    // Set default date and time - updated for datetime-local
    function setDefaultDateTime() {
        const orderDateElement = document.getElementById('orderDate');
        if (orderDateElement && !orderDateElement.value) {
            const now = new Date();
            const year = now.getFullYear();
            const month = String(now.getMonth() + 1).padStart(2, '0');
            const day = String(now.getDate()).padStart(2, '0');
            const hours = String(now.getHours()).padStart(2, '0');
            const minutes = String(now.getMinutes()).padStart(2, '0');
            orderDateElement.value = `${year}-${month}-${day}T${hours}:${minutes}`;
        }
    }

    // Helper function to format datetime
    function formatDateTime(dateString) {
        if (!dateString) return 'N/A';
        return new Date(dateString).toLocaleString('vi-VN', {
            year: 'numeric',
            month: '2-digit',
            day: '2-digit',
            hour: '2-digit',
            minute: '2-digit'
        });
    }

    // Updated useCoupon function to work with new simplified form
    window.useCoupon = function(couponCode) {
        console.log('Using coupon:', couponCode);

        switchTab('order');

        // Fill coupon code in order form
        const couponCodeInput = document.getElementById('orderCouponCode');
        if (couponCodeInput) {
            couponCodeInput.value = couponCode;
        }

        // Focus on amount field
        const amountInput = document.getElementById('totalAmount');
        if (amountInput) {
            amountInput.focus();
        }
    };

    // Coupon Management Functions
    async function loadUserCoupons(page = 0, size = 10) {
        if (!globalState.auth.token || !globalState.auth.userId) {
            console.log('No auth data, skipping coupon load');
            return;
        }

        if (globalState.coupons.loading) {
            console.log('Already loading coupons, skipping');
            return;
        }

        console.log('Loading user coupons...', { page, size, userId: globalState.auth.userId });

        globalState.coupons.loading = true;
        globalState.coupons.currentPage = page;
        globalState.coupons.pageSize = size;

        const couponGrid = document.getElementById('couponGrid');
        const paginationContainer = document.getElementById('paginationContainer');

        if (couponGrid) {
            couponGrid.innerHTML = '<div class="loading-spinner">Đang tải...</div>';
        }
        if (paginationContainer) {
            paginationContainer.style.display = 'none';
        }

        try {
            const res = await fetch(ENDPOINTS.userCoupons(globalState.auth.userId, page, size), {
                headers: { 'Authorization': 'Bearer ' + globalState.auth.token }
            });

            if (!res.ok) {
                throw new Error(`HTTP ${res.status}: ${res.statusText}`);
            }

            const data = await res.json();
            console.log('Coupons loaded:', data);

            // Update global state với structure từ API
            globalState.coupons.data = data.userCoupons || [];
            globalState.coupons.totalCount = data.totalCount || 0;
            globalState.coupons.currentPage = data.page !== undefined ? data.page : page;
            globalState.coupons.pageSize = data.size !== undefined ? data.size : size;

            // Render data
            renderCoupons();
            renderPagination();

        } catch (err) {
            console.error('Failed to load coupons:', err);
            if (couponGrid) {
                couponGrid.innerHTML = `
                    <div class="error-state">
                        <h3>Lỗi kết nối</h3>
                        <p>Không thể tải danh sách mã giảm giá: ${err.message}</p>
                        <button class="btn btn-primary" onclick="retryLoadCoupons()">
                            Thử lại
                        </button>
                    </div>
                `;
            }
        } finally {
            globalState.coupons.loading = false;
        }
    }

    function renderCoupons() {
        const couponGrid = document.getElementById('couponGrid');
        if (!couponGrid) return;

        const coupons = globalState.coupons.data;

        if (!coupons || coupons.length === 0) {
            couponGrid.innerHTML = `
                <div class="empty-state">
                    <h3>Chưa có mã giảm giá</h3>
                    <p>Bạn chưa có mã giảm giá nào.</p>
                </div>
            `;
            return;
        }

        couponGrid.innerHTML = coupons.map(coupon => createCouponCard(coupon)).join('');
    }

    function renderPagination() {
        const paginationContainer = document.getElementById('paginationContainer');
        const paginationInfo = document.getElementById('paginationInfo');
        const paginationControls = document.getElementById('paginationControls');

        if (!paginationContainer || !paginationInfo || !paginationControls) return;

        const { currentPage, pageSize, totalCount } = globalState.coupons;
        const totalPages = Math.ceil(totalCount / pageSize);

        if (totalPages <= 1) {
            paginationContainer.style.display = 'none';
            return;
        }

        // Show pagination container
        paginationContainer.style.display = 'flex';

        // Update info
        const startItem = currentPage * pageSize + 1;
        const endItem = Math.min((currentPage + 1) * pageSize, totalCount);
        paginationInfo.textContent = `Hiển thị ${startItem}-${endItem} trong tổng ${totalCount} mã`;

        // Create pagination controls
        let controlsHTML = '';

        // Previous button
        if (currentPage > 0) {
            controlsHTML += `<button class="page-btn" onclick="goToPage(${currentPage - 1})">‹ Trước</button>`;
        }

        // Page numbers
        const startPage = Math.max(0, currentPage - 2);
        const endPage = Math.min(totalPages - 1, currentPage + 2);

        if (startPage > 0) {
            controlsHTML += `<button class="page-btn" onclick="goToPage(0)">1</button>`;
            if (startPage > 1) {
                controlsHTML += `<span class="page-btn" style="border:none;cursor:default">...</span>`;
            }
        }

        for (let i = startPage; i <= endPage; i++) {
            const activeClass = i === currentPage ? 'active' : '';
            controlsHTML += `<button class="page-btn ${activeClass}" onclick="goToPage(${i})">${i + 1}</button>`;
        }

        if (endPage < totalPages - 1) {
            if (endPage < totalPages - 2) {
                controlsHTML += `<span class="page-btn" style="border:none;cursor:default">...</span>`;
            }
            controlsHTML += `<button class="page-btn" onclick="goToPage(${totalPages - 1})">${totalPages}</button>`;
        }

        // Next button
        if (currentPage < totalPages - 1) {
            controlsHTML += `<button class="page-btn" onclick="goToPage(${currentPage + 1})">Sau ›</button>`;
        }

        paginationControls.innerHTML = controlsHTML;
    }

    // Helper functions - simplified
    function createCouponCard(coupon) {
        const statusClass = getStatusClass(coupon.status);
        const statusText = getStatusText(coupon.status);
        const discountDisplay = getDiscountDisplay(coupon.type, coupon.value);
        const isExpired = new Date(coupon.endDate) < new Date();
        console.log("New Date", new Date());
        console.log("Coupon End Date", coupon.endDate);
        const isActive = coupon.status === 'ACTIVE' && !isExpired;

        return `
            <div class="coupon-card ${isActive ? 'active' : 'inactive'}">
                <div class="coupon-code">${sanitizeHtml(coupon.couponCode)}</div>
                <div class="coupon-discount">${discountDisplay}</div>
                <div class="coupon-description">${sanitizeHtml(coupon.description)}</div>
                <div class="coupon-meta">
                    <span>${formatDateRange(coupon.startDate, coupon.endDate)}</span>
                </div>
                ${isActive ?
                    `<button class="btn-use-coupon" onclick="useCoupon('${sanitizeHtml(coupon.couponCode)}')">
                        Sử dụng ngay
                    </button>` :
                    `<div class="coupon-unavailable">${getUnavailableReason(coupon.status, isExpired)}</div>`
                }
            </div>
        `;
    }

    function getStatusClass(status) {
        const statusMap = {
            'ACTIVE': 'status-active',
            'EXPIRED': 'status-expired',
            'USED': 'status-used',
            'INACTIVE': 'status-inactive'
        };
        return statusMap[status] || 'status-inactive';
    }

    function getStatusText(status) {
        const statusMap = {
            'ACTIVE': 'Khả dụng',
            'EXPIRED': 'Hết hạn',
            'USED': 'Đã dùng',
            'INACTIVE': 'Tạm khóa'
        };
        return statusMap[status] || 'Không rõ';
    }

    function getDiscountDisplay(type, value) {
        if (type === 'PERCENTAGE') {
            return `<span class="discount-percentage">${value}% OFF</span>`;
        } else if (type === 'FIXED_AMOUNT') {
            return `<span class="discount-fixed">-${formatCurrency(value)}</span>`;
        } else if (type === 'FREE_SHIPPING') {
            return `<span class="discount-shipping">MIỄN PHÍ SHIP</span>`;
        }
        return `<span class="discount-other">${value}</span>`;
    }

    function formatDateRange(startDate, endDate) {
        const start = new Date(startDate).toLocaleDateString('vi-VN');
        const end = new Date(endDate).toLocaleDateString('vi-VN');
        return `${start} - ${end}`;
    }

    function getUnavailableReason(status, isExpired) {
        if (isExpired) return 'Đã hết hạn';
        if (status === 'USED') return 'Đã sử dụng';
        if (status === 'INACTIVE') return 'Tạm khóa';
        return 'Không khả dụng';
    }

    function formatCurrency(amount) {
        return new Intl.NumberFormat('vi-VN', {
            style: 'currency',
            currency: 'VND'
        }).format(amount);
    }

    // Event listener for page size change
    const pageSizeSelect = document.getElementById('pageSize');
    if (pageSizeSelect) {
        pageSizeSelect.addEventListener('change', function() {
            const newSize = parseInt(this.value);
            globalState.coupons.pageSize = newSize;
            globalState.coupons.currentPage = 0; // Reset to first page
            loadUserCoupons(0, newSize);
        });
    }

    // Global functions
    window.goToPage = function(page) {
        loadUserCoupons(page, globalState.coupons.pageSize);
    };

    window.retryLoadCoupons = function() {
        loadUserCoupons(globalState.coupons.currentPage, globalState.coupons.pageSize);
    };

    window.fillSampleOrder = function(amount) {
        document.getElementById('totalAmount').value = amount.toLocaleString('vi-VN');
        document.getElementById('totalAmount').focus();
    };

    window.validateCoupon = async function() {
        const couponCode = document.getElementById('orderCouponCode')?.value?.trim();
        const validation = document.getElementById('couponValidation');

        if (!validation) return;

        if (!couponCode) {
            validation.innerHTML = '<div class="invalid">❌ Vui lòng nhập mã coupon</div>';
            return;
        }

        validation.innerHTML = '<div>⏳ Đang kiểm tra...</div>';

        // Check if coupon exists in user's coupons
        const userCoupons = globalState.coupons.data;
        const foundCoupon = userCoupons.find(c => c.couponCode === couponCode);

        if (foundCoupon) {
            const isValid = foundCoupon.status === 'ACTIVE' && new Date(foundCoupon.endDate) >= new Date();
            if (isValid) {
                validation.innerHTML = `<div class="valid">✅ Mã hợp lệ - ${foundCoupon.description}</div>`;
            } else {
                validation.innerHTML = `<div class="invalid">❌ Mã không khả dụng hoặc đã hết hạn</div>`;
            }
        } else {
            validation.innerHTML = '<div class="invalid">❌ Mã không tồn tại hoặc không thuộc về bạn</div>';
        }
    };

    window.createNewOrder = function() {
        clearOrderForm();
        document.getElementById('totalAmount').focus();
    };

    window.showAvailableCoupons = function() {
        switchTab('coupons');
    };

    // Helper function to format date (for backward compatibility)
    function formatDate(dateString) {
        return formatDateTime(dateString);
    }

    function setDefaultDate() {
        setDefaultDateTime();
    }

    function initialize() {
        console.log('Initializing app with auth state:', globalState.auth);

        setDefaultDate();

        if (globalState.auth.token && globalState.auth.userId) {
            showApp();
            // Initialize with coupons tab
            switchTab('coupons');
        } else {
            showLogin();
        }
    }

    // Start the app
    initialize();
});
