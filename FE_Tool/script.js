// Admin Panel JavaScript
class AdminPanel {
    constructor() {
        console.log('AdminPanel constructor called');
        this.baseURL = 'http://localhost:8080/api/v1';
        this.token = localStorage.getItem('adminToken');
        this.currentUser = localStorage.getItem('adminUser');
        this.coupons = [];
        this.rules = [];
        this.collections = [];

        console.log('Token:', this.token ? 'exists' : 'not found');
        console.log('Current user:', this.currentUser);
        
        this.init();
    }

    async init() {
        console.log('AdminPanel init called');
        // Check if user is logged in and has admin role
        if (!this.token || !this.isAdmin()) {
            console.log('User not logged in or not admin, redirecting to login');
            this.redirectToLogin();
            return;
        }

        console.log('User is logged in and is admin, setting up panel');
        this.setupEventListeners();
        this.setupNavigation();
        this.loadUserInfo();
        this.loadDashboardStats();

        // Test API connection on startup
        await this.testAPIConnection();
    }

    isAdmin() {
        // Check if current user has admin role
        const userData = JSON.parse(localStorage.getItem('userData') || '{}');
        return userData.role === 'ADMIN';
    }

    redirectToLogin() {
        // Don't redirect, just show login form
        this.showLoginForm();
    }

    showLoginForm() {
        document.getElementById('loginForm').style.display = 'flex';
        document.getElementById('mainContent').style.display = 'none';
    }

    setupEventListeners() {
        // Auth form
        const authForm = document.getElementById('authForm');
        if (authForm) {
            authForm.addEventListener('submit', (e) => {
                e.preventDefault();
                this.handleLogin();
            });
        }

        // Logout button
        const logoutBtn = document.getElementById('logoutBtn');
        if (logoutBtn) {
            logoutBtn.addEventListener('click', () => {
                this.logout();
            });
        }

        // Form submissions
        const updateCouponForm = document.getElementById('updateCouponForm');
        if (updateCouponForm) {
            updateCouponForm.addEventListener('submit', (e) => {
                e.preventDefault();
                this.handleUpdateCoupon(e.target);
            });
        }

        const updateRuleForm = document.getElementById('updateRuleForm');
        if (updateRuleForm) {
            updateRuleForm.addEventListener('submit', (e) => {
                e.preventDefault();
                this.handleUpdateRule(e.target);
            });
        }

        const updateCollectionForm = document.getElementById('updateCollectionForm');
        if (updateCollectionForm) {
            updateCollectionForm.addEventListener('submit', (e) => {
                e.preventDefault();
                this.handleUpdateCollection(e.target);
            });
        }

        // Discount type change handler
        const discountType = document.getElementById('discountType');
        if (discountType) {
            discountType.addEventListener('change', (e) => {
                this.handleDiscountTypeChange(e.target.value);
            });
        }

        // Rule type change handler
        const ruleType = document.getElementById('ruleType');
        if (ruleType) {
            ruleType.addEventListener('change', (e) => {
                this.updateRuleConfigFields(e.target.value, {});
            });
        }
    }

    setupNavigation() {
        const navItems = document.querySelectorAll('.nav-item');
        navItems.forEach(item => {
            item.addEventListener('click', (e) => {
                e.preventDefault();
                const section = item.dataset.section;
                this.switchSection(section);

                // Update active nav item
                navItems.forEach(nav => nav.classList.remove('active'));
                item.classList.add('active');
            });
        });
    }

    switchSection(sectionName) {
        console.log('switchSection called with:', sectionName);
        // Hide all sections
        document.querySelectorAll('.content-section').forEach(section => {
            section.classList.remove('active');
        });

        // Show target section
        const targetSection = document.getElementById(sectionName);
        if (targetSection) {
            targetSection.classList.add('active');
            console.log('Target section activated:', sectionName);
        } else {
            console.error('Target section not found:', sectionName);
        }

        // Auto load data when switching sections
        if (sectionName === 'coupons') {
            console.log('Auto loading coupons...');
            this.loadCoupons();
            // Also preload collections for dropdown
            setTimeout(() => {
                this.loadCollectionDropdown();
            }, 500); // Small delay to ensure coupons are loaded first
        } else if (sectionName === 'rules') {
            console.log('Auto loading rules...');
            this.loadRules();
        } else if (sectionName === 'collections') {
            console.log('Auto loading collections...');
            this.loadCollections();
        }
        
        // Ensure collection dropdown is loaded when coupons section is active
        if (sectionName === 'coupons') {
            // Add a small delay to ensure the section is fully rendered
            setTimeout(() => {
                const collectionKeySelect = document.getElementById('collectionKey');
                if (collectionKeySelect && collectionKeySelect.options.length <= 1) {
                    console.log('Collection dropdown is empty, reloading...');
                    this.loadCollectionDropdown();
                }
            }, 1000);
        }
    }

    loadUserInfo() {
        const userData = JSON.parse(localStorage.getItem('userData') || '{}');
        const currentUserElement = document.getElementById('currentUser');
        if (userData.username && currentUserElement) {
            currentUserElement.textContent = userData.username;
        }
    }

    async loadDashboardStats() {
        // Load actual stats from APIs
        try {
            const [couponsResponse, rulesResponse, collectionsResponse] = await Promise.allSettled([
                fetch(`${this.baseURL}/coupons?size=1`, {
                    headers: { 'Authorization': `Bearer ${this.token}` }
                }),
                fetch(`${this.baseURL}/rules?size=1`, {
                    headers: { 'Authorization': `Bearer ${this.token}` }
                }),
                fetch(`${this.baseURL}/rules/collections?size=1`, {
                    headers: { 'Authorization': `Bearer ${this.token}` }
                })
            ]);

            const stats = {
                coupons: 0,
                rules: 0,
                collections: 0
            };

            if (couponsResponse.status === 'fulfilled' && couponsResponse.value.ok) {
                const data = await couponsResponse.value.json();
                stats.coupons = data.totalElements || 0;
            }

            if (rulesResponse.status === 'fulfilled' && rulesResponse.value.ok) {
                const data = await rulesResponse.value.json();
                stats.rules = data.totalElements || 0;
            }

            if (collectionsResponse.status === 'fulfilled' && collectionsResponse.value.ok) {
                const data = await collectionsResponse.value.json();
                stats.collections = data.data?.totalElements || 0;
            }

            const totalCouponsElement = document.getElementById('totalCoupons');
            const totalRulesElement = document.getElementById('totalRules');
            const totalCollectionsElement = document.getElementById('totalCollections');

            if (totalCouponsElement) totalCouponsElement.textContent = stats.coupons;
            if (totalRulesElement) totalRulesElement.textContent = stats.rules;
            if (totalCollectionsElement) totalCollectionsElement.textContent = stats.collections;
        } catch (error) {
            console.error('Error loading dashboard stats:', error);
            // Fallback to mock data
            const stats = {
                coupons: Math.floor(Math.random() * 50) + 10,
                rules: Math.floor(Math.random() * 20) + 5,
                collections: Math.floor(Math.random() * 10) + 3
            };

            const totalCouponsElement = document.getElementById('totalCoupons');
            const totalRulesElement = document.getElementById('totalRules');
            const totalCollectionsElement = document.getElementById('totalCollections');

            if (totalCouponsElement) totalCouponsElement.textContent = stats.coupons;
            if (totalRulesElement) totalRulesElement.textContent = stats.rules;
            if (totalCollectionsElement) totalCollectionsElement.textContent = stats.collections;
        }
    }

    async loadCoupons() {
        console.log('loadCoupons called');
        const couponList = document.getElementById('couponList');
        if (!couponList) {
            console.error('couponList element not found in loadCoupons');
            return;
        }

        try {
            console.log('Calling API:', `${this.baseURL}/coupons`);
            // Call actual API
            const response = await fetch(`${this.baseURL}/coupons`, {
                method: 'GET',
                headers: {
                    'Authorization': `Bearer ${this.token}`,
                    'Content-Type': 'application/json'
                }
            });

            if (response.ok) {
                const data = await response.json();
                console.log('API Response:', data);
                
                // Handle paginated response
                const coupons = data.coupons || data;
                const totalElements = data.totalElements || coupons.length;
                
                console.log('Extracted coupons:', coupons);
                console.log('Total elements:', totalElements);
                
                // Store coupons for editing
                this.coupons = coupons;
                
                this.displayCoupons(coupons);
                
                const totalCouponsElement = document.getElementById('totalCoupons');
                if (totalCouponsElement) {
                    totalCouponsElement.textContent = totalElements;
                }
                
            } else {
                console.error('API response not ok:', response.status, response.statusText);
                throw new Error(`HTTP ${response.status}: ${response.statusText}`);
            }

        } catch (error) {
            console.error('Error loading coupons:', error);
            
            // Fallback to mock data if API fails
            const mockCoupons = [
                {
                    couponId: 1,
                    code: "SAVE10",
                    title: "10% Discount",
                    description: "10% discount for orders from 100k",
                    isActive: true,
                    type: "PERCENTAGE",
                    config: { value: 10, maxDiscount: 50000 }
                },
                {
                    couponId: 2,
                    code: "FLAT20K",
                    title: "20k Discount",
                    description: "Fixed 20k discount for orders from 50k",
                    isActive: false,
                    type: "FIXED_AMOUNT",
                    config: { value: 20000 }
                },
                {
                    couponId: 3,
                    code: "SUMMER15",
                    title: "Summer Sale 15%",
                    description: "15% discount for summer",
                    isActive: true,
                    type: "PERCENTAGE",
                    config: { value: 15, maxDiscount: 100000 }
                }
            ];

            // Store mock coupons for editing
            this.coupons = mockCoupons;
            this.displayCoupons(mockCoupons);
        }
    }

    async loadRules() {
        try {
            const response = await fetch(`${this.baseURL}/rules`, {
                method: 'GET',
                headers: {
                    'Authorization': `Bearer ${this.token}`,
                    'Content-Type': 'application/json'
                }
            });

            if (response.ok) {
                const data = await response.json();
                const rules = data.rules || data;
                this.rules = rules; // Store for collections and editing
                this.displayRules(rules);
            } else {
                throw new Error(`HTTP ${response.status}: ${response.statusText}`);
            }

        } catch (error) {
            console.error('Error loading rules:', error);
            
            // Fallback to mock data
            const mockRules = [
                {
                    ruleId: 1,
                    description: "Minimum order amount rule",
                    ruleType: "MIN_ORDER_AMOUNT",
                    isActive: true,
                    ruleConfiguration: { min_amount: 100000, maxDiscount: 50000 }
                },
                {
                    ruleId: 2,
                    description: "Daily active time rule",
                    ruleType: "DAILY_ACTIVE_TIME",
                    isActive: true,
                    ruleConfiguration: { start_time: "09:00", end_time: "22:00" }
                }
            ];

            this.rules = mockRules; // Store for collections and editing
            this.displayRules(mockRules);
        }
    }

    displayRules(rules) {
        const ruleList = document.getElementById('ruleList');
        if (!ruleList) return;

        if (!rules || rules.length === 0) {
            ruleList.innerHTML = '<p>No rules available</p>';
            return;
        }

        ruleList.innerHTML = rules.map(rule => {
            const ruleId = rule.ruleId || rule.id;
            const description = rule.description || 'No description';
            const ruleType = rule.ruleType || rule.type || 'UNKNOWN';
            const isActive = rule.isActive !== undefined ? rule.isActive : true;
            const status = rule.status || (isActive ? 'ACTIVE' : 'INACTIVE');

            return `
                <div class="rule-item" onclick="window.adminPanel.editRule(${ruleId})">
                    <div class="rule-info">
                        <div class="rule-id">${ruleId}</div>
                        <div class="rule-details">
                            <h4>${ruleType}</h4>
                            <p>${description}</p>
                        </div>
                    </div>
                    <div class="rule-status ${isActive ? 'active' : 'inactive'}">
                        ${status === 'ACTIVE' ? 'Active' : 'Inactive'}
                    </div>
                    <div class="rule-actions">
                        <button class="btn btn-primary" onclick="event.stopPropagation(); window.adminPanel.editRule(${ruleId})">
                            <i class="fas fa-edit"></i>
                            Edit
                        </button>
                    </div>
                </div>
            `;
        }).join('');
    }

    async loadCollections() {
        try {
            const response = await fetch(`${this.baseURL}/rules/collections`, {
                method: 'GET',
                headers: {
                    'Authorization': `Bearer ${this.token}`,
                    'Content-Type': 'application/json'
                }
            });

            if (response.ok) {
                const data = await response.json();
                const collections = data.data?.collections || data.collections || data;
                this.collections = collections; // Store for editing
                this.displayCollections(collections);
            } else {
                throw new Error(`HTTP ${response.status}: ${response.statusText}`);
            }

        } catch (error) {
            console.error('Error loading collections:', error);
            
            // Fallback to mock data
            const mockCollections = [
                {
                    collectionId: [1, 7],
                    name: "Lunch Promo 300k+",
                    description: "Rules applied to all coupons",
                    isActive: true
                },
                {
                    collectionId: [2, 7],
                    name: "Lunch Promo 700k+",
                    description: "Rules applied to all coupons",
                    isActive: true
                }
            ];

            this.collections = mockCollections; // Store for editing
            this.displayCollections(mockCollections);
        }
    }

    displayCollections(collections) {
        const collectionList = document.getElementById('collectionList');
        if (!collectionList) return;

        if (!collections || collections.length === 0) {
            collectionList.innerHTML = '<p>No collections available</p>';
            return;
        }

        collectionList.innerHTML = collections.map(collection => {
            console.log('Processing collection:', collection);
            const collectionId = Array.isArray(collection.collectionId) ? collection.collectionId[0] : collection.collectionId;
            const name = collection.name || 'No name';
            const description = collection.description || 'No description';
            const isActive = collection.isActive !== undefined ? collection.isActive : true;
            const status = collection.status || (isActive ? 'ACTIVE' : 'INACTIVE');

            return `
                <div class="collection-item" onclick="window.adminPanel.editCollection(${collectionId})">
                    <div class="collection-info">
                        <div class="collection-id">${collectionId}</div>
                        <div class="collection-details">
                            <h4>${name}</h4>
                            <p>${description}</p>
                        </div>
                    </div>
                    <div class="collection-status ${isActive ? 'active' : 'inactive'}">
                        ${status === 'ACTIVE' ? 'Active' : 'Inactive'}
                    </div>
                    <div class="collection-actions">
                        <button class="btn btn-primary" onclick="event.stopPropagation(); window.adminPanel.editCollection(${collectionId})">
                            <i class="fas fa-edit"></i>
                            Edit
                        </button>
                    </div>
                </div>
            `;
        }).join('');
    }

    displayCoupons(coupons) {
        console.log('displayCoupons called with:', coupons);
        const couponList = document.getElementById('couponList');
        if (!couponList) {
            console.error('couponList element not found');
            return;
        }

        if (!coupons || coupons.length === 0) {
            console.log('No coupons to display');
            couponList.innerHTML = '<p>No coupons available</p>';
            return;
        }

        couponList.innerHTML = coupons.map(coupon => {
            console.log('Processing coupon:', coupon);
            // Handle different data formats from API vs mock
            const couponId = coupon.couponId || coupon.id;
            const code = coupon.code;
            const title = coupon.title || 'No title';
            const description = coupon.description || 'No description';
            const isActive = coupon.isActive !== undefined ? coupon.isActive : (coupon.status === 'ACTIVE');
            const status = coupon.status || (isActive ? 'ACTIVE' : 'INACTIVE');
            const type = coupon.type || (coupon.config?.type || 'UNKNOWN');
            
            // Get config values
            const config = coupon.config || {};
            const discountValue = config.value || 0;
            const maxDiscount = config.max_discount || config.maxDiscount || 0;
            const discountType = type; // Use the main type, not config.type
            
            console.log('Coupon processed:', { couponId, code, title, discountType, discountValue, maxDiscount });
            
            // Format discount display
            let discountDisplay = '';
            if (discountType === 'PERCENTAGE') {
                discountDisplay = `${discountValue}%`;
                if (maxDiscount > 0) {
                    discountDisplay += ` (max ${this.formatCurrency(maxDiscount)})`;
                }
            } else if (discountType === 'FIXED_AMOUNT') {
                discountDisplay = this.formatCurrency(discountValue);
            }

            const html = `
                <div class="coupon-item" onclick="window.adminPanel.editCoupon(${couponId})">
                    <div class="coupon-info">
                        <div class="coupon-code">${code}</div>
                        <div class="coupon-details">
                            <h4>${title}</h4>
                            <p>${description}</p>
                            <small class="discount-info">
                                <i class="fas fa-tag"></i>
                                Discount: ${discountDisplay}
                            </small>
                            ${coupon.collectionKeyId ? `<small class="collection-info">
                                <i class="fas fa-key"></i>
                                Collection: ${coupon.collectionKeyId}
                            </small>` : ''}
                            ${coupon.endDate ? `<small class="expire-info">
                                <i class="fas fa-calendar-times"></i>
                                Expires: ${this.formatExpireDate(coupon.endDate)}
                            </small>` : ''}
                        </div>
                    </div>
                    <div class="coupon-status ${isActive ? 'active' : 'inactive'}">
                        ${status === 'ACTIVE' ? 'Active' : 'Inactive'}
                    </div>
                    <div class="coupon-actions">
                        <button class="btn btn-primary" onclick="event.stopPropagation(); window.adminPanel.editCoupon(${couponId})">
                            <i class="fas fa-edit"></i>
                            Edit
                        </button>
                    </div>
                </div>
            `;
            return html;
        }).join('');
        
        console.log('Final HTML to be set:', couponList.innerHTML);
        console.log('Coupon list element:', couponList);
    }

    async editCoupon(couponId) {
        // Find coupon from the loaded list
        const coupon = this.findCouponById(couponId);
        
        if (coupon) {
            console.log('Editing coupon:', coupon);
            console.log('Coupon collectionKey:', coupon.collectionKeyId);
            
            // Show edit view first
            this.showCouponEditView();
            
            // Fill form data
            this.fillCouponForm(coupon);
            
            // Load collections and set the current collection key
            await this.loadCollectionDropdown(coupon.collectionKeyId);
            
            // Double-check and set the value after a short delay
            setTimeout(() => {
                const collectionKeySelect = document.getElementById('collectionKey');
                if (collectionKeySelect && coupon.collectionKeyId) {
                    console.log('Final check - Setting collectionKey to:', coupon.collectionKeyId);
                    collectionKeySelect.value = coupon.collectionKey.toString();
                    
                    // Trigger change event to ensure UI updates
                    const event = new Event('change', { bubbles: true });
                    collectionKeySelect.dispatchEvent(event);
                }
            }, 100);
        } else {
            console.error('Coupon not found:', couponId);
            this.showToast('error', 'Error', 'Coupon not found');
        }
    }

    findCouponById(couponId) {
        // Search in the current coupons list
        if (this.coupons && this.coupons.length > 0) {
            return this.coupons.find(coupon => 
                (coupon.couponId || coupon.id) === couponId
            );
        }
        return null;
    }

    fillCouponForm(coupon) {
        // Fill form with coupon data
        document.getElementById('couponId').value = coupon.couponId || coupon.id;
        document.getElementById('couponCode').value = coupon.code || '';
        document.getElementById('couponTitle').value = coupon.title || '';
        document.getElementById('couponDescription').value = coupon.description || '';
        
        const isActive = coupon.isActive !== undefined ? coupon.isActive : (coupon.status === 'ACTIVE');
        document.getElementById('couponStatus').value = isActive.toString();

        const config = coupon.config || {};
        const type = coupon.type || config.type || 'PERCENTAGE';
        
        if (type) {
            document.getElementById('couponType').value = type;
        }
        
        if (config.value) {
            document.getElementById('couponValue').value = config.value;
        }
        
        // Show/hide max discount container based on type
        const maxDiscountContainer = document.getElementById('maxDiscountContainer');
        if (type === 'PERCENTAGE') {
            maxDiscountContainer.style.display = 'block';
            if (config.max_discount) {
                document.getElementById('couponMaxDiscount').value = config.max_discount;
            } else if (config.maxDiscount) {
                document.getElementById('couponMaxDiscount').value = config.maxDiscount;
            }
        } else {
            maxDiscountContainer.style.display = 'none';
            document.getElementById('couponMaxDiscount').value = '';
        }

        // Set the collectionKey value directly first
        const collectionKeySelect = document.getElementById('collectionKey');
        if (collectionKeySelect && coupon.collectionKeyId) {
            console.log('Setting collectionKey value directly:', coupon.collectionKeyId);
            collectionKeySelect.value = coupon.collectionKeyId.toString();
            
            // Also set the selected attribute on the option if it exists
            const options = collectionKeySelect.querySelectorAll('option');
            options.forEach(option => {
                if (option.value === coupon.collectionKeyId.toString()) {
                    option.selected = true;
                    console.log('Set selected attribute on option:', option.value);
                } else {
                    option.selected = false;
                }
            });
        }

        // Set expire date
        const expireDateInput = document.getElementById('couponExpireDate');
        if (expireDateInput && coupon.endDate) {
            // Convert expire date to datetime-local format
            const expireDate = new Date(coupon.endDate);
            const localDateTime = expireDate.toISOString().slice(0, 16); // Format: YYYY-MM-DDTHH:MM
            expireDateInput.value = localDateTime;
            console.log('Set expire date:', localDateTime);
        } else if (expireDateInput) {
            // Set minimum date to today if no expire date
            const today = new Date();
            const localDateTime = today.toISOString().slice(0, 16);
            expireDateInput.min = localDateTime;
        }
    }

    editRule(ruleId) {
        // Find rule from the loaded list
        const rule = this.findRuleById(ruleId);
        
        if (rule) {
            this.fillRuleForm(rule);
            this.showRuleEditView();
        } else {
            console.error('Rule not found:', ruleId);
            this.showToast('error', 'Error', 'Rule not found');
        }
    }

    findRuleById(ruleId) {
        // Search in the current rules list
        if (this.rules && this.rules.length > 0) {
            return this.rules.find(rule => 
                (rule.ruleId || rule.id) === ruleId
            );
        }
        return null;
    }

    fillRuleForm(rule) {
        // Fill form with rule data
        document.getElementById('ruleId').value = rule.ruleId || rule.id;
        document.getElementById('ruleDescription').value = rule.description || '';
        
        const isActive = rule.isActive !== undefined ? rule.isActive : true;
        document.getElementById('ruleStatus').value = isActive.toString();

        const ruleType = rule.ruleType || rule.type || 'MIN_ORDER_AMOUNT';
        document.getElementById('ruleType').value = ruleType;

        // Get configuration from either ruleConfiguration or ruleConfig
        const config = rule.ruleConfiguration || rule.ruleConfig || {};
        console.log('Filling rule form with config:', config);
        
        // Update config fields based on rule type
        this.updateRuleConfigFields(ruleType, config);
    }

    updateRuleConfigFields(ruleType, config) {
        const container = document.getElementById('ruleConfigContainer');
        if (!container) return;

        container.innerHTML = '';

        if (ruleType === 'MIN_ORDER_AMOUNT') {
            container.innerHTML = `
                <div class="config-item">
                    <label>Min Amount (VND):</label>
                    <input type="number" name="min_amount" value="${config.min_amount || ''}" step="1000" min="0" placeholder="Nhập số tiền tối thiểu">
                </div>
            `;
        } else if (ruleType === 'DAILY_ACTIVE_TIME') {
            // Convert HH:MM:SS to HH:MM for input type="time"
            const startTime = config.start_time ? config.start_time.substring(0, 5) : '';
            const endTime = config.end_time ? config.end_time.substring(0, 5) : '';
            
            container.innerHTML = `
                <div class="config-item">
                    <label>Start Time:</label>
                    <input type="time" name="start_time" value="${startTime}" required>
                </div>
                <div class="config-item">
                    <label>End Time:</label>
                    <input type="time" name="end_time" value="${endTime}" required>
                </div>
            `;
        }
    }

    showRuleEditView() {
        document.getElementById('ruleListView').style.display = 'none';
        document.getElementById('ruleEditView').style.display = 'block';
    }

    showRuleList() {
        document.getElementById('ruleEditView').style.display = 'none';
        document.getElementById('ruleListView').style.display = 'block';
    }

    editCollection(collectionId) {
        // Find collection from the loaded list
        const collection = this.findCollectionById(collectionId);
        
        if (collection) {
            this.fillCollectionForm(collection);
            this.showCollectionEditView();
        } else {
            console.error('Collection not found:', collectionId);
            this.showToast('error', 'Error', 'Collection not found');
        }
    }

    findCollectionById(collectionId) {
        // Search in the current collections list
        if (this.collections && this.collections.length > 0) {
            return this.collections.find(collection => {
                const id = Array.isArray(collection.collectionId) ? collection.collectionId[0] : collection.collectionId;
                return id === collectionId;
            });
        }
        return null;
    }

    fillCollectionForm(collection) {
        // Fill form with collection data
        const collectionId = Array.isArray(collection.collectionId) ? collection.collectionId[0] : collection.collectionId;
        document.getElementById('collectionId').value = collectionId || collection.id;
        document.getElementById('collectionName').value = collection.name || '';
        document.getElementById('collectionDescription').value = collection.description || '';

        // Load rules for checkboxes
        this.loadRuleCheckboxes(collection.ruleIds || []);
    }

    loadRuleCheckboxes(selectedRuleIds) {
        const container = document.getElementById('ruleCheckboxes');
        if (!container || !this.rules.length) return;

        container.innerHTML = this.rules.map(rule => {
            const ruleId = rule.ruleId || rule.id;
            const isChecked = selectedRuleIds.includes(ruleId);
            return `
                <div class="rule-checkbox-item">
                    <input type="checkbox" id="rule_${ruleId}" name="ruleIds" value="${ruleId}" ${isChecked ? 'checked' : ''}>
                    <label for="rule_${ruleId}">${rule.ruleType || rule.type} - ${rule.description || 'Không có mô tả'}</label>
                </div>
            `;
        }).join('');
    }

    showCollectionEditView() {
        document.getElementById('collectionListView').style.display = 'none';
        document.getElementById('collectionEditView').style.display = 'block';
    }

    showCollectionList() {
        document.getElementById('collectionEditView').style.display = 'none';
        document.getElementById('collectionListView').style.display = 'block';
    }

    showCouponEditView() {
        document.getElementById('couponListView').style.display = 'none';
        document.getElementById('couponEditView').style.display = 'block';
    }

    showCouponList() {
        document.getElementById('couponEditView').style.display = 'none';
        document.getElementById('couponListView').style.display = 'block';
    }

    async loadCollectionDropdown(selectedCollectionKey = '') {
        const collectionKeySelect = document.getElementById('collectionKey');
        if (!collectionKeySelect) {
            console.error('Collection key select element not found');
            return;
        }

        console.log('Loading collection dropdown with selected key:', selectedCollectionKey);
        
        // Store the selected key for later use
        this.pendingCollectionKey = selectedCollectionKey;

        try {
            // Load collections from API
            const response = await fetch(`${this.baseURL}/rules/collections`, {
                method: 'GET',
                headers: {
                    'Authorization': `Bearer ${this.token}`,
                    'Content-Type': 'application/json'
                }
            });

            if (response.ok) {
                const data = await response.json();
                const collections = data.data?.collections || data.collections || data;
                
                console.log('API response collections:', collections);
                
                // Clear existing options
                collectionKeySelect.innerHTML = '<option value="">-- Select Collection --</option>';
                
                // Add collection options
                collections.forEach(collection => {
                    const collectionId = Array.isArray(collection.collectionId) ? collection.collectionId[0] : collection.collectionId;
                    const option = document.createElement('option');
                    option.value = collectionId;
                    option.textContent = `${collectionId} - ${collection.name || 'No name'}`;
                    
                    // Set selected if matches the coupon's collectionKey
                    if (collectionId.toString() === selectedCollectionKey?.toString()) {
                        option.selected = true;
                        console.log('Selected option:', collectionId, 'for key:', selectedCollectionKey);
                    }
                    
                    collectionKeySelect.appendChild(option);
                });
                
                // Force set the value if we have a selectedCollectionKey
                if (selectedCollectionKey) {
                    collectionKeySelect.value = selectedCollectionKey.toString();
                    console.log('Force set collectionKey value to:', selectedCollectionKey);
                    
                    // Trigger change event to ensure UI updates
                    const event = new Event('change', { bubbles: true });
                    collectionKeySelect.dispatchEvent(event);
                }
                
                console.log('Final dropdown value:', collectionKeySelect.value);
                console.log('Collection dropdown loaded with', collections.length, 'collections');
                
                // Ensure the correct option is selected
                this.ensureCollectionKeySelected(selectedCollectionKey);
            } else {
                console.error('Failed to load collections for dropdown, status:', response.status);
                // Fallback to empty dropdown
                collectionKeySelect.innerHTML = '<option value="">-- Select Collection --</option>';
            }
        } catch (error) {
            console.error('Error loading collections for dropdown:', error);
            // Fallback to empty dropdown
            collectionKeySelect.innerHTML = '<option value="">-- Select Collection --</option>';
        }
    }
    
    ensureCollectionKeySelected(collectionKey) {
        if (!collectionKey) return;
        
        const collectionKeySelect = document.getElementById('collectionKey');
        if (!collectionKeySelect) return;
        
        // Try multiple approaches to ensure selection
        collectionKeySelect.value = collectionKey.toString();
        
        // Also set selected attribute on the option
        const options = collectionKeySelect.querySelectorAll('option');
        options.forEach(option => {
            if (option.value === collectionKey.toString()) {
                option.selected = true;
                console.log('Ensured option selected:', option.value);
            } else {
                option.selected = false;
            }
        });
        
        // Trigger change event
        const event = new Event('change', { bubbles: true });
        collectionKeySelect.dispatchEvent(event);
        
        console.log('Final ensured dropdown value:', collectionKeySelect.value);
    }

    async handleLogin() {
        const username = document.getElementById('username').value;
        const password = document.getElementById('password').value;

        console.log('Login attempt with:', username);

        try {
            const response = await fetch(`${this.baseURL}/auth/login`, {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({ username, password })
            });

            const result = await response.json();
            console.log('Login response:', result);

            if (response.ok && result.success && result.role === 'ADMIN') {
                console.log('Login successful, setting up admin panel');
                this.token = result.token;
                localStorage.setItem('adminToken', this.token);
                localStorage.setItem('userData', JSON.stringify(result));
                localStorage.setItem('adminUser', result.username);
                
                this.showMainContent();
                const currentUserElement = document.getElementById('currentUser');
                if (currentUserElement) {
                    currentUserElement.textContent = result.username;
                }
                this.showToast('success', 'Success', 'Login successful!');
                
                // Initialize admin panel after successful login
                this.init();
            } else {
                console.log('Login failed:', result);
                this.showToast('error', 'Error', 'Login failed or not admin');
            }
        } catch (error) {
            console.error('Login error:', error);
            this.showToast('error', 'Error', 'An error occurred while connecting to server');
        }
    }

    showMainContent() {
        console.log('showMainContent called');
        const loginForm = document.getElementById('loginForm');
        const mainContent = document.getElementById('mainContent');
        
        if (loginForm) {
            loginForm.style.display = 'none';
            console.log('Login form hidden');
        } else {
            console.error('Login form element not found');
        }
        
        if (mainContent) {
            mainContent.style.display = 'block';
            console.log('Main content shown');
        } else {
            console.error('Main content element not found');
        }
    }

    handleDiscountTypeChange(discountType) {
        const discountValueInput = document.getElementById('discountValue');
        const maxDiscountInput = document.getElementById('couponMaxDiscount');
        
        if (discountType === 'PERCENTAGE') {
            if (discountValueInput) {
                discountValueInput.placeholder = 'Nhập phần trăm (ví dụ: 10)';
                discountValueInput.max = '100';
            }
            if (maxDiscountInput) {
                maxDiscountInput.placeholder = 'Giảm giá tối đa (VND)';
                maxDiscountInput.disabled = false;
            }
        } else if (discountType === 'FIXED_DISCOUNT') {
            if (discountValueInput) {
                discountValueInput.placeholder = 'Nhập số tiền (VND)';
                discountValueInput.removeAttribute('max');
            }
            if (maxDiscountInput) {
                maxDiscountInput.placeholder = 'Không cần thiết cho giảm giá cố định';
                maxDiscountInput.value = '';
                maxDiscountInput.disabled = true;
            }
        }
    }

    async handleUpdateRule(form) {
        const formData = new FormData(form);
        const ruleId = formData.get('ruleId');

        // Build request body
        const requestBody = {};

        // Add optional fields
        requestBody.ruleId = ruleId;
        if (formData.get('description')) requestBody.description = formData.get('description');
        if (formData.get('isActive')) requestBody.isActive = formData.get('isActive') === 'true';
        if (formData.get('ruleType')) requestBody.ruleType = formData.get('ruleType');

        // Build rule config based on rule type
        const ruleConfig = {};
        const ruleType = formData.get('ruleType');
        
        if (ruleType === 'MIN_ORDER_AMOUNT') {
            const minAmount = formData.get('min_amount');
            if (minAmount) {
                ruleConfig.type = 'MIN_ORDER_AMOUNT';
                ruleConfig.min_amount = parseFloat(minAmount);
            }
        } else if (ruleType === 'DAILY_ACTIVE_TIME') {
            const startTime = formData.get('start_time');
            const endTime = formData.get('end_time');
            if (startTime && endTime) {
                ruleConfig.type = 'DAILY_ACTIVE_TIME';
                // Convert HH:MM to HH:MM:SS format
                ruleConfig.start_time = startTime + ':00';
                ruleConfig.end_time = endTime + ':00';
            }
        }

        // Always include ruleConfig if we have any configuration
        if (Object.keys(ruleConfig).length > 0) {
            requestBody.ruleConfig = ruleConfig;
        }

        try {
            console.log('Calling API to update rule:', ruleId);
            console.log('Request body:', requestBody);

            const response = await fetch(`${this.baseURL}/rules/${ruleId}`, {
                method: 'PUT',
                headers: {
                    'Content-Type': 'application/json',
                    'Authorization': `Bearer ${this.token}`
                },
                body: JSON.stringify(requestBody)
            });

            console.log('API Response status:', response.status);

            if (response.ok) {
                const result = await response.json();
                console.log('API Response:', result);
                
                this.showToast('success', 'Success', 'Rule updated successfully via API');
                this.showRuleList();
                this.loadRules(); // Refresh list
                this.loadDashboardStats(); // Refresh stats
            } else {
                const error = await response.json();
                console.error('API Error:', error);
                this.showToast('error', 'API Error', error.message || `HTTP ${response.status}: An error occurred while updating rule`);
            }
        } catch (error) {
            console.error('Network Error:', error);
            this.showToast('error', 'Connection Error', 'Cannot connect to server. Please check your network connection.');
        }
    }

    async handleUpdateCollection(form) {
        const formData = new FormData(form);
        const collectionId = formData.get('collectionId');

        if (!collectionId) {
            this.showToast('error', 'Error', 'Please enter Collection ID');
            return;
        }

        // Build request body
        const requestBody = {
            collectionId: parseInt(collectionId)
        };

        // Add optional fields
        if (formData.get('name')) requestBody.name = formData.get('name');
        if (formData.get('description')) requestBody.description = formData.get('description');

        // Get selected rule IDs
        const selectedRuleIds = [];
        const checkboxes = form.querySelectorAll('input[name="ruleIds"]:checked');
        checkboxes.forEach(checkbox => {
            selectedRuleIds.push(parseInt(checkbox.value));
        });

        if (selectedRuleIds.length > 0) {
            requestBody.ruleIds = selectedRuleIds;
        }

        try {
            console.log('Calling API to update collection:', collectionId);
            console.log('Request body:', requestBody);

            const response = await fetch(`${this.baseURL}/rules/collections`, {
                method: 'PUT',
                headers: {
                    'Content-Type': 'application/json',
                    'Authorization': `Bearer ${this.token}`
                },
                body: JSON.stringify(requestBody)
            });

            console.log('API Response status:', response.status);

            if (response.ok) {
                const result = await response.json();
                console.log('API Response:', result);
                
                this.showToast('success', 'Success', 'Collection updated successfully via API');
                this.showCollectionList();
                this.loadCollections(); // Refresh list
                this.loadDashboardStats(); // Refresh stats
            } else {
                const error = await response.json();
                console.error('API Error:', error);
                this.showToast('error', 'API Error', error.message || `HTTP ${response.status}: An error occurred while updating collection`);
            }
        } catch (error) {
            console.error('Network Error:', error);
            this.showToast('error', 'Connection Error', 'Cannot connect to server. Please check your network connection.');
        }
    }

    async handleUpdateCoupon(form) {
        const formData = new FormData(form);
        const couponId = formData.get('couponId');

        if (!couponId) {
            this.showToast('error', 'Error', 'Please enter Coupon ID');
            return;
        }

        const requestBody = {
            code: formData.get('code')
        };

        if (formData.get('title')) requestBody.title = formData.get('title');
        if (formData.get('description')) requestBody.description = formData.get('description');
        if (formData.get('isActive')) requestBody.isActive = formData.get('isActive') === 'true';
        
        // Handle collectionKey
        const collectionKey = formData.get('collectionKey');
        if (collectionKey) {
            requestBody.collectionKeyId = parseInt(collectionKey);
        } else {
            // If no collection selected, set to null or remove the field
            requestBody.collectionKeyId = null;
        }

        const expireDate = formData.get('expireDate');
        if (expireDate) {
            // Convert local datetime to custom format: 2025-07-16T13:20:08
            const expireDateTime = new Date(expireDate);
            const year = expireDateTime.getFullYear();
            const month = String(expireDateTime.getMonth() + 1).padStart(2, '0');
            const day = String(expireDateTime.getDate()).padStart(2, '0');
            const hours = String(expireDateTime.getHours()).padStart(2, '0');
            const minutes = String(expireDateTime.getMinutes()).padStart(2, '0');
            const seconds = String(expireDateTime.getSeconds()).padStart(2, '0');
            
            requestBody.endDate = `${year}-${month}-${day}T${hours}:${minutes}:${seconds}`;
            console.log('Setting expire date:', requestBody.endDate);
        }

        const config = {};
        const discountType = formData.get('type'); // This is readonly now
        const discountValue = formData.get('value');
        const maxDiscount = formData.get('max_discount');

        // Type cannot be changed, so we don't send it in update
        if (discountValue) config.value = parseFloat(discountValue);
        
        // Use max_discount field to match API format
        if (maxDiscount && discountType === 'PERCENTAGE') {
            config.max_discount = parseFloat(maxDiscount);
        }

        if (Object.keys(config).length > 0) {
            requestBody.config = config;
        }

        try {
            console.log('Calling API to update coupon:', couponId);
            console.log('Request body:', requestBody);

            const response = await fetch(`${this.baseURL}/coupons/${couponId}`, {
                method: 'PUT',
                headers: {
                    'Content-Type': 'application/json',
                    'Authorization': `Bearer ${this.token}`
                },
                body: JSON.stringify(requestBody)
            });

            console.log('API Response status:', response.status);

            if (response.ok) {
                const result = await response.json();
                console.log('API Response:', result);
                
                this.showToast('success', 'Success', 'Coupon updated successfully via API');
                this.showCouponList();
                this.loadCoupons(); // Refresh list
                this.loadDashboardStats(); // Refresh stats
            } else {
                const error = await response.json();
                console.error('API Error:', error);
                this.showToast('error', 'API Error', error.message || `HTTP ${response.status}: An error occurred while updating coupon`);
            }
        } catch (error) {
            console.error('Network Error:', error);
            this.showToast('error', 'Connection Error', 'Cannot connect to server. Please check your network connection.');
        }
    }



    async testAPIConnection() {
        console.log('Testing API connection...');
        
        const endpoints = [
            { name: 'List Coupons', url: '/coupons', method: 'GET' },
            { name: 'List Rules', url: '/rules', method: 'GET' },
            { name: 'List Collections', url: '/rules/collections', method: 'GET' },
            { name: 'Get Coupon', url: '/coupons/1', method: 'GET' },
            { name: 'Get Rule Collection', url: '/rules/collections/1', method: 'GET' }
        ];

        const results = [];

        for (const endpoint of endpoints) {
            try {
                const response = await fetch(`${this.baseURL}${endpoint.url}`, {
                    method: endpoint.method,
                    headers: {
                        'Authorization': `Bearer ${this.token}`,
                        'Content-Type': 'application/json'
                    }
                });

                results.push({
                    name: endpoint.name,
                    url: endpoint.url,
                    status: response.status,
                    available: response.status !== 404
                });

                console.log(`${endpoint.name}: ${response.status} ${response.statusText}`);
            } catch (error) {
                results.push({
                    name: endpoint.name,
                    url: endpoint.url,
                    status: 'ERROR',
                    available: false,
                    error: error.message
                });
                console.log(`${endpoint.name}: ERROR - ${error.message}`);
            }
        }

        // Display results
        const availableAPIs = results.filter(r => r.available);
        const unavailableAPIs = results.filter(r => !r.available);

        console.log('=== API TEST RESULTS ===');
        console.log('Available APIs:', availableAPIs.map(r => r.name));
        console.log('Unavailable APIs:', unavailableAPIs.map(r => r.name));

        return results;
    }

    showToast(type, title, message) {
        const toastContainer = document.getElementById('toastContainer');
        if (!toastContainer) return;

        const toast = document.createElement('div');
        toast.className = `toast ${type}`;

        const icon = this.getToastIcon(type);

        toast.innerHTML = `
            <i class="${icon}"></i>
            <div class="toast-content">
                <div class="toast-title">${title}</div>
                <div class="toast-message">${message}</div>
            </div>
        `;

        toastContainer.appendChild(toast);

        // Auto remove after 5 seconds
        setTimeout(() => {
            if (toast.parentNode) {
                toast.parentNode.removeChild(toast);
            }
        }, 5000);

        // Click to remove
        toast.addEventListener('click', () => {
            if (toast.parentNode) {
                toast.parentNode.removeChild(toast);
            }
        });
    }

    getToastIcon(type) {
        switch (type) {
            case 'success': return 'fas fa-check-circle';
            case 'error': return 'fas fa-exclamation-circle';
            case 'warning': return 'fas fa-exclamation-triangle';
            case 'info': return 'fas fa-info-circle';
            default: return 'fas fa-info-circle';
        }
    }

    logout() {
        localStorage.removeItem('adminToken');
        localStorage.removeItem('adminUser');
        localStorage.removeItem('userData');
        window.location.reload();
    }

    formatCurrency(amount) {
        return new Intl.NumberFormat('vi-VN', {
            style: 'currency',
            currency: 'VND'
        }).format(amount);
    }

    formatExpireDate(dateString) {
        const date = new Date(dateString);
        const now = new Date();
        const diffTime = date.getTime() - now.getTime();
        const diffDays = Math.ceil(diffTime / (1000 * 60 * 60 * 24));
        
        if (diffDays < 0) {
            return 'Expired';
        } else if (diffDays === 0) {
            return 'Expires today';
        } else if (diffDays === 1) {
            return 'Expires tomorrow';
        } else if (diffDays <= 7) {
            return `Expires in ${diffDays} days`;
        } else {
            // Format: 2025-07-16T13:20:08
            const year = date.getFullYear();
            const month = String(date.getMonth() + 1).padStart(2, '0');
            const day = String(date.getDate()).padStart(2, '0');
            const hours = String(date.getHours()).padStart(2, '0');
            const minutes = String(date.getMinutes()).padStart(2, '0');
            const seconds = String(date.getSeconds()).padStart(2, '0');
            
            return `${year}-${month}-${day}T${hours}:${minutes}:${seconds}`;
        }
    }
}

function switchSection(sectionName) {
    console.log('Global switchSection called with:', sectionName);
    if (window.adminPanel) {
        console.log('AdminPanel instance found, calling switchSection');
        window.adminPanel.switchSection(sectionName);

        document.querySelectorAll('.nav-item').forEach(nav => {
            nav.classList.remove('active');
            if (nav.dataset.section === sectionName) {
                nav.classList.add('active');
            }
        });
    } else {
        console.error('AdminPanel instance not found in global switchSection');
    }
}

// Global function to load coupons
function loadCoupons() {
    console.log('Global loadCoupons called');
    if (window.adminPanel) {
        console.log('AdminPanel instance found, calling loadCoupons');
        window.adminPanel.loadCoupons();
    } else {
        console.error('AdminPanel instance not found');
    }
}

// Global function to show coupon list
function showCouponList() {
    if (window.adminPanel) {
        window.adminPanel.showCouponList();
    }
}

// Global function to load rules
function loadRules() {
    if (window.adminPanel) {
        window.adminPanel.loadRules();
    }
}

// Global function to show rule list
function showRuleList() {
    if (window.adminPanel) {
        window.adminPanel.showRuleList();
    }
}

function loadCollections() {
    if (window.adminPanel) {
        window.adminPanel.loadCollections();
    }
}

// Global function to show collection list
function showCollectionList() {
    if (window.adminPanel) {
        window.adminPanel.showCollectionList();
    }
}

document.addEventListener('DOMContentLoaded', () => {
    console.log('DOM loaded, initializing admin panel...');
    
    // Check if user is already logged in
    const token = localStorage.getItem('adminToken');
    const userData = JSON.parse(localStorage.getItem('userData') || '{}');
    
    console.log('Token:', token ? 'exists' : 'not found');
    console.log('User data:', userData);
    
    if (token && userData.role === 'ADMIN') {
        console.log('User is logged in, showing main content...');
        document.getElementById('loginForm').style.display = 'none';
        document.getElementById('mainContent').style.display = 'block';
        window.adminPanel = new AdminPanel();
        console.log('AdminPanel instance created:', window.adminPanel);
    } else {
        console.log('User is not logged in, showing login form...');
        // User is not logged in, show login form
        document.getElementById('loginForm').style.display = 'flex';
        document.getElementById('mainContent').style.display = 'none';
        
        // Setup login form
        const authForm = document.getElementById('authForm');
        if (authForm) {
            authForm.addEventListener('submit', (e) => {
                e.preventDefault();
                if (!window.adminPanel) {
                    window.adminPanel = new AdminPanel();
                }
                window.adminPanel.handleLogin();
            });
        }
    }
});

document.addEventListener('DOMContentLoaded', () => {
    const today = new Date().toISOString().split('T')[0];
    const dateInputs = document.querySelectorAll('input[type="date"]');
    dateInputs.forEach(input => {
        if (!input.value) {
            input.min = today;
        }
    });
});

function validateCouponForm(form) {
    const couponId = form.couponId.value;
    const couponCode = form.code.value;

    if (!couponId || !couponCode) {
        return false;
    }

    return true;
}

function validateRuleForm(form) {
    const ruleId = form.ruleId.value;

    if (!ruleId) {
        return false;
    }

    return true;
}

// Utility functions
function formatCurrency(amount) {
    return new Intl.NumberFormat('vi-VN', {
        style: 'currency',
        currency: 'VND'
    }).format(amount);
}

function formatDate(dateString) {
    return new Date(dateString).toLocaleDateString('vi-VN');
}

function autoSaveForm(formId) {
    const form = document.getElementById(formId);
    if (!form) return;

    const savedData = localStorage.getItem(`form_${formId}`);
    if (savedData) {
        const data = JSON.parse(savedData);
        Object.keys(data).forEach(key => {
            const input = form.querySelector(`[name="${key}"]`);
            if (input) {
                input.value = data[key];
            }
        });
    }

    // Save on change
    form.addEventListener('input', () => {
        const formData = new FormData(form);
        const data = {};
        for (let [key, value] of formData.entries()) {
            data[key] = value;
        }
        localStorage.setItem(`form_${formId}`, JSON.stringify(data));
    });
}

// Initialize auto-save for forms
document.addEventListener('DOMContentLoaded', () => {
    setTimeout(() => {
        autoSaveForm('updateCouponForm');
        autoSaveForm('updateRuleForm');
    }, 1000);
});
