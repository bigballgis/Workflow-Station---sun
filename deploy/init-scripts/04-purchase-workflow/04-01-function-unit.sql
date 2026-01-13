-- 閲囪喘鐢宠鍔熻兘鍗曞厓
INSERT INTO dw_function_units (code, name, description, icon_id, status, current_version, created_by)
VALUES (
    'fu-purchase-request',
    '閲囪喘鐢宠',
    '閲囪喘鐢宠娴佺▼锛屾敮鎸佸绾у鎵广€侀噾棰濆垎绾с€侀儴闂ㄤ細绛剧瓑鍔熻兘锛岃鐩栨墍鏈?绉嶄换鍔″垎閰嶇被鍨嬪拰8绉嶅姩浣滅被鍨?,
    (SELECT id FROM dw_icons WHERE name = 'credit-card'),
    'DRAFT',
    NULL,
    'system'
);