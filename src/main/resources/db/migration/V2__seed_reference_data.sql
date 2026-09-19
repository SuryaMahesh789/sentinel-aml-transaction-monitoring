-- =============================================================================
-- V2__seed_reference_data.sql
-- Sentinel AML — Reference Data Seed
-- Exchange rates, high-risk jurisdictions, alert rules
-- =============================================================================

-- -----------------------------------------------------------------------------
-- Exchange Rates (to INR, as of approximate 2024 rates)
-- -----------------------------------------------------------------------------
INSERT INTO exchange_rates (from_currency, to_currency, rate, is_active, effective_from) VALUES
('INR', 'INR', 1.000000,    TRUE, '2024-01-01 00:00:00'),
('USD', 'INR', 83.500000,   TRUE, '2024-01-01 00:00:00'),
('EUR', 'INR', 90.200000,   TRUE, '2024-01-01 00:00:00'),
('GBP', 'INR', 105.500000,  TRUE, '2024-01-01 00:00:00'),
('AED', 'INR', 22.730000,   TRUE, '2024-01-01 00:00:00'),
('SGD', 'INR', 62.100000,   TRUE, '2024-01-01 00:00:00'),
('JPY', 'INR', 0.560000,    TRUE, '2024-01-01 00:00:00'),
('CHF', 'INR', 94.200000,   TRUE, '2024-01-01 00:00:00'),
('CAD', 'INR', 61.800000,   TRUE, '2024-01-01 00:00:00'),
('AUD', 'INR', 54.500000,   TRUE, '2024-01-01 00:00:00');

-- -----------------------------------------------------------------------------
-- High-Risk / Sanctioned Jurisdictions
-- Sources: FATF High-Risk list, OFAC SDN, UN Security Council sanctions
-- -----------------------------------------------------------------------------
INSERT INTO high_risk_jurisdictions (country_code, country_name, risk_level, reason, is_active) VALUES
-- OFAC / UN sanctioned
('KP', 'North Korea',                  'SANCTIONED', 'UN Security Council comprehensive sanctions', TRUE),
('IR', 'Iran',                         'SANCTIONED', 'OFAC comprehensive sanctions program',        TRUE),
('SY', 'Syria',                        'SANCTIONED', 'OFAC comprehensive sanctions program',        TRUE),
('CU', 'Cuba',                         'SANCTIONED', 'OFAC comprehensive sanctions program',        TRUE),
('RU', 'Russia',                       'SANCTIONED', 'OFAC/EU broad sanctions post-2022',           TRUE),
('BY', 'Belarus',                      'SANCTIONED', 'EU and US targeted financial sanctions',      TRUE),
-- FATF High-Risk (increased monitoring)
('MM', 'Myanmar',                      'HIGH',       'FATF High-Risk Jurisdiction',                 TRUE),
('AF', 'Afghanistan',                  'HIGH',       'FATF High-Risk Jurisdiction',                 TRUE),
('YE', 'Yemen',                        'HIGH',       'FATF High-Risk — terrorism financing risk',   TRUE),
('IQ', 'Iraq',                         'HIGH',       'FATF High-Risk — AML deficiencies',           TRUE),
('SD', 'Sudan',                        'HIGH',       'FATF High-Risk Jurisdiction',                 TRUE),
('LY', 'Libya',                        'HIGH',       'FATF High-Risk Jurisdiction',                 TRUE),
('SO', 'Somalia',                      'HIGH',       'FATF High-Risk — no effective AML regime',    TRUE),
('VU', 'Vanuatu',                      'HIGH',       'FATF High-Risk — offshore AML risk',          TRUE),
('HT', 'Haiti',                        'HIGH',       'FATF High-Risk Jurisdiction',                 TRUE);

-- -----------------------------------------------------------------------------
-- Alert Rules — configurable thresholds
-- Parameters stored as JSON strings for flexibility
-- -----------------------------------------------------------------------------
INSERT INTO alert_rules (rule_code, rule_name, description, enabled, base_risk_score, parameters) VALUES

('CTR_THRESHOLD',
 'Currency Transaction Report Threshold',
 'Flag any single transaction >= $10,000 (INR equivalent) for mandatory CTR review.',
 TRUE, 60,
 '{"thresholdInr": 833000, "currency": "INR"}'),

('STRUCTURING',
 'Structuring / Smurfing Detection',
 'Three or more transactions from the same account within 24 hours, each between $9,000–$9,999 equivalent.',
 TRUE, 75,
 '{"minAmountInr": 749700, "maxAmountInr": 832999, "countThreshold": 3, "windowHours": 24}'),

('RAPID_MOVEMENT',
 'Rapid Movement of Funds',
 'Funds deposited and >= 80% transferred/withdrawn within 48 hours (layering indicator).',
 TRUE, 80,
 '{"outflowPercentThreshold": 80, "windowHours": 48}'),

('HIGH_RISK_JURISDICTION',
 'High-Risk / Sanctioned Jurisdiction Transfer',
 'Any transaction to/from a jurisdiction on the high-risk or sanctions list.',
 TRUE, 85,
 '{}'),

('BEHAVIORAL_DEVIATION',
 'Unusual Volume / Behavioral Deviation',
 'Customer daily transaction volume exceeds 3x their 90-day rolling average.',
 TRUE, 70,
 '{"rollingDays": 90, "multiplierThreshold": 3.0}');
