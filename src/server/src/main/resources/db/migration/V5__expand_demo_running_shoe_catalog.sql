-- Add lower and mid-priced demo shoes so shoppers can try multi-item carts
-- within the local demo authorization limit.
INSERT INTO merchant.products
    (product_id, name, description, category, price, currency, image_url, is_active, available_quantity)
VALUES
    ('shoe-004', 'Stride Tempo', 'Lightweight trainer for quick everyday runs', 'RUNNING_SHOES', 129.00, 'USD', 'https://images.unsplash.com/photo-1460353581641-37baddab0fa2', TRUE, 12),
    ('shoe-005', 'Stride Cloud', 'Soft cushioned comfort for easy miles', 'RUNNING_SHOES', 99.00, 'USD', 'https://images.unsplash.com/photo-1495555961986-6d4c1ecb7be3', TRUE, 12),
    ('shoe-006', 'Stride Pace', 'Dependable entry-level road running shoe', 'RUNNING_SHOES', 79.00, 'USD', 'https://images.unsplash.com/photo-1600185365483-26d7a4cc7519', TRUE, 15),
    ('shoe-007', 'Stride Sprint', 'Responsive shoe for speed sessions and training', 'RUNNING_SHOES', 119.00, 'USD', 'https://images.unsplash.com/photo-1539185441755-769473a23570', TRUE, 10),
    ('shoe-008', 'Stride Endurance', 'Extra comfort for long-distance running', 'RUNNING_SHOES', 159.00, 'USD', 'https://images.unsplash.com/photo-1552346154-21d32810aba3', TRUE, 8),
    ('shoe-009', 'Stride Flex', 'Flexible everyday shoe for road and gym runs', 'RUNNING_SHOES', 89.00, 'USD', 'https://images.unsplash.com/photo-1542291026-7eec264c27ff', TRUE, 14),
    ('shoe-010', 'Stride Horizon', 'Stable support for steady daily mileage', 'RUNNING_SHOES', 139.00, 'USD', 'https://images.unsplash.com/photo-1460353581641-37baddab0fa2', TRUE, 9)
ON CONFLICT (product_id) DO NOTHING;
