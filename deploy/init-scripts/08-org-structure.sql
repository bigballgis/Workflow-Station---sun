-- 08-org-structure.sql
-- 创建组织架构、虚拟组和用户关系
-- 用于流程任务分配

-- ============================================
-- 1. 同步sys_user到act_id_user
-- ============================================
INSERT INTO act_id_user (id_, rev_, first_, last_, display_name_, email_, pwd_)
SELECT 
  username,
  1,
  SPLIT_PART(display_name, ' ', 1),
  COALESCE(NULLIF(SPLIT_PART(display_name, ' ', 2), ''), display_name),
  display_name,
  email,
  password_hash
FROM sys_user
WHERE status = 'ACTIVE'
ON CONFLICT (id_) DO UPDATE SET
  display_name_ = EXCLUDED.display_name_,
  email_ = EXCLUDED.email_;

-- ============================================
-- 2. 创建部门组（组织架构）
-- ============================================
INSERT INTO act_id_group (id_, rev_, name_, type_) VALUES
  ('dept_admin', 1, '管理部门', 'department'),
  ('dept_dev', 1, '研发部门', 'department'),
  ('dept_qa', 1, '测试部门', 'department'),
  ('dept_sales', 1, '销售部门', 'department'),
  ('dept_hr', 1, '人力资源部', 'department'),
  ('dept_finance', 1, '财务部门', 'department')
ON CONFLICT (id_) DO UPDATE SET name_ = EXCLUDED.name_;

-- ============================================
-- 3. 创建虚拟组（角色组）
-- ============================================
INSERT INTO act_id_group (id_, rev_, name_, type_) VALUES
  ('role_admin', 1, '系统管理员', 'role'),
  ('role_manager', 1, '部门经理', 'role'),
  ('role_hr', 1, 'HR人员', 'role'),
  ('role_finance', 1, '财务人员', 'role'),
  ('role_developer', 1, '开发人员', 'role'),
  ('role_tester', 1, '测试人员', 'role'),
  ('role_employee', 1, '普通员工', 'role')
ON CONFLICT (id_) DO UPDATE SET name_ = EXCLUDED.name_;

-- ============================================
-- 4. 创建用户与部门的关系
-- ============================================
DELETE FROM act_id_membership;

-- 管理部门
INSERT INTO act_id_membership (user_id_, group_id_) VALUES
  ('super_admin', 'dept_admin'),
  ('system_admin', 'dept_admin'),
  ('tenant_admin', 'dept_admin'),
  ('auditor', 'dept_admin');

-- 研发部门
INSERT INTO act_id_membership (user_id_, group_id_) VALUES
  ('dev_lead', 'dept_dev'),
  ('senior_dev', 'dept_dev'),
  ('developer', 'dept_dev'),
  ('designer', 'dept_dev');

-- 测试部门
INSERT INTO act_id_membership (user_id_, group_id_) VALUES
  ('tester', 'dept_qa');

-- 销售部门
INSERT INTO act_id_membership (user_id_, group_id_) VALUES
  ('manager', 'dept_sales'),
  ('team_lead', 'dept_sales'),
  ('employee_a', 'dept_sales'),
  ('employee_b', 'dept_sales');

-- HR部门
INSERT INTO act_id_membership (user_id_, group_id_) VALUES
  ('hr_staff', 'dept_hr');

-- 财务部门
INSERT INTO act_id_membership (user_id_, group_id_) VALUES
  ('finance', 'dept_finance');

-- ============================================
-- 5. 创建用户与角色组的关系
-- ============================================
-- 系统管理员角色
INSERT INTO act_id_membership (user_id_, group_id_) VALUES
  ('super_admin', 'role_admin'),
  ('system_admin', 'role_admin');

-- 部门经理角色
INSERT INTO act_id_membership (user_id_, group_id_) VALUES
  ('manager', 'role_manager'),
  ('dev_lead', 'role_manager'),
  ('team_lead', 'role_manager');

-- HR角色
INSERT INTO act_id_membership (user_id_, group_id_) VALUES
  ('hr_staff', 'role_hr');

-- 财务角色
INSERT INTO act_id_membership (user_id_, group_id_) VALUES
  ('finance', 'role_finance');

-- 开发人员角色
INSERT INTO act_id_membership (user_id_, group_id_) VALUES
  ('dev_lead', 'role_developer'),
  ('senior_dev', 'role_developer'),
  ('developer', 'role_developer'),
  ('designer', 'role_developer');

-- 测试人员角色
INSERT INTO act_id_membership (user_id_, group_id_) VALUES
  ('tester', 'role_tester');

-- 普通员工角色
INSERT INTO act_id_membership (user_id_, group_id_) VALUES
  ('employee_a', 'role_employee'),
  ('employee_b', 'role_employee');

-- ============================================
-- 6. 创建上下级关系表（用于获取manager）
-- ============================================
CREATE TABLE IF NOT EXISTS sys_user_hierarchy (
  user_id VARCHAR(64) NOT NULL,
  manager_id VARCHAR(64),
  PRIMARY KEY (user_id)
);

-- 清空并重建上下级关系
DELETE FROM sys_user_hierarchy;

INSERT INTO sys_user_hierarchy (user_id, manager_id) VALUES
  -- 销售部门：员工 -> 团队主管 -> 部门经理
  ('employee_a', 'team_lead'),
  ('employee_b', 'team_lead'),
  ('team_lead', 'manager'),
  ('manager', NULL),
  
  -- 研发部门：开发人员 -> 开发组长
  ('developer', 'dev_lead'),
  ('senior_dev', 'dev_lead'),
  ('designer', 'dev_lead'),
  ('dev_lead', NULL),
  
  -- 测试部门
  ('tester', 'dev_lead'),
  
  -- HR部门
  ('hr_staff', NULL),
  
  -- 财务部门
  ('finance', NULL),
  
  -- 管理部门
  ('system_admin', 'super_admin'),
  ('tenant_admin', 'super_admin'),
  ('auditor', 'super_admin'),
  ('super_admin', NULL);

-- ============================================
-- 7. 添加更多测试用户（模拟真实场景）
-- ============================================
-- 添加更多HR人员
INSERT INTO sys_user (id, username, password_hash, display_name, email, department_id, status, created_at, updated_at)
VALUES 
  (gen_random_uuid(), 'hr_manager', '$2a$10$N.zmdr9k7uOCQb376NoUnuTJ8iAt6Z5EHsM8lE9lBOsl7iAt6Z5EH', 'HR经理', 'hr_manager@example.com', 'HR', 'ACTIVE', NOW(), NOW()),
  (gen_random_uuid(), 'hr_assistant', '$2a$10$N.zmdr9k7uOCQb376NoUnuTJ8iAt6Z5EHsM8lE9lBOsl7iAt6Z5EH', 'HR助理', 'hr_assistant@example.com', 'HR', 'ACTIVE', NOW(), NOW())
ON CONFLICT (username) DO NOTHING;

-- 同步新用户到Flowable
INSERT INTO act_id_user (id_, rev_, first_, last_, display_name_, email_)
SELECT username, 1, display_name, display_name, display_name, email
FROM sys_user 
WHERE username IN ('hr_manager', 'hr_assistant')
ON CONFLICT (id_) DO NOTHING;

-- 添加新HR用户到HR部门和角色
INSERT INTO act_id_membership (user_id_, group_id_) 
SELECT username, 'dept_hr' FROM sys_user WHERE username IN ('hr_manager', 'hr_assistant')
ON CONFLICT DO NOTHING;

INSERT INTO act_id_membership (user_id_, group_id_) 
SELECT username, 'role_hr' FROM sys_user WHERE username IN ('hr_manager', 'hr_assistant')
ON CONFLICT DO NOTHING;

-- 添加HR上下级关系
INSERT INTO sys_user_hierarchy (user_id, manager_id) VALUES
  ('hr_assistant', 'hr_manager'),
  ('hr_manager', NULL)
ON CONFLICT (user_id) DO UPDATE SET manager_id = EXCLUDED.manager_id;

-- 更新原hr_staff的上级为hr_manager
UPDATE sys_user_hierarchy SET manager_id = 'hr_manager' WHERE user_id = 'hr_staff';
