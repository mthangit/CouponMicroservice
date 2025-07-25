

INSERT INTO `rule` (`id`, `type`, `description`, `rule_configuration`, `created_at`, `updated_at`)
VALUES
(1, 'MIN_ORDER_AMOUNT', 'Order >= 300,000',
 JSON_OBJECT('type','MIN_ORDER_AMOUNT','min_amount',300000), NOW(), NOW()),

(2, 'MIN_ORDER_AMOUNT', 'Order >= 700,000',
 JSON_OBJECT('type','MIN_ORDER_AMOUNT','min_amount',700000), NOW(), NOW()),

(3, 'MIN_ORDER_AMOUNT', 'Order >= 1,500,000',
 JSON_OBJECT('type','MIN_ORDER_AMOUNT','min_amount',1500000), NOW(), NOW()),

(4, 'MIN_ORDER_AMOUNT', 'Order >= 3,000,000',
 JSON_OBJECT('type','MIN_ORDER_AMOUNT','min_amount',3000000), NOW(), NOW()),

(6, 'MIN_ORDER_AMOUNT', 'Order >= 4,000,000',
 JSON_OBJECT('type','MIN_ORDER_AMOUNT','min_amount',4000000), NOW(), NOW()),

(7, 'DAILY_ACTIVE_TIME', 'Active from 12:00 to 14:00 daily',
 JSON_OBJECT('type','DAILY_ACTIVE_TIME','start_time','12:00:00','end_time','14:00:00'), NOW(), NOW()),

(8, 'DAILY_ACTIVE_TIME', 'Active from 17:00 to 20:00 daily',
 JSON_OBJECT('type','DAILY_ACTIVE_TIME','start_time','17:00:00','end_time','20:00:00'), NOW(), NOW());

INSERT INTO `rule_collection` (`id`, `name`, `rule_ids`, `created_at`, `updated_at`)
VALUES
(1, 'Lunch Promo 300k+',
 JSON_ARRAY(1,7),
 NOW(), NOW()),

(2, 'Lunch Promo 700k+',
 JSON_ARRAY(2,7),
 NOW(), NOW()),

(3, 'Lunch Promo 1.5M+',
 JSON_ARRAY(3,7),
 NOW(), NOW()),

(4, 'Evening Sale 300k+',
 JSON_ARRAY(1,8),
 NOW(), NOW()),

(5, 'Evening Sale 1.5M+',
 JSON_ARRAY(3,8),
 NOW(), NOW()),

(6, 'Big Order Midday',
 JSON_ARRAY(4,7),
 NOW(), NOW()),

(7, 'Big Order Evening',
 JSON_ARRAY(4,8),
 NOW(), NOW()),

(8, 'Top Order Midday',
 JSON_ARRAY(6,7),
 NOW(), NOW()),

(9, 'Top Order Evening',
 JSON_ARRAY(6,8),
 NOW(), NOW()),

(10, 'Anytime 700k+',
 JSON_ARRAY(2),
 NOW(), NOW());
