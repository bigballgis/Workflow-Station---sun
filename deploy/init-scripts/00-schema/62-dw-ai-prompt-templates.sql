-- =====================================================
-- 62. Developer Workstation: AI prompt template overrides (dw_ai_prompt_templates)
--     三段提示词（REQUIREMENTS / DESIGN / GENERATION）的运行时覆盖值。
--     内置默认值仍是 backend/developer-workstation/src/main/resources/ai-prompts/*.txt；
--     本表**没有种子行**——没有行 = 用镜像内的内置默认值，有行 = 用行里的 content。
--     这样运维改提示词不必重新构建/部署，而未被覆盖的相位继续跟随代码仓库演进。
-- =====================================================
CREATE TABLE IF NOT EXISTS dw_ai_prompt_templates (
    id BIGSERIAL PRIMARY KEY,
    phase VARCHAR(20) NOT NULL,
    content TEXT NOT NULL,
    updated_by VARCHAR(64),
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_dw_ai_prompt_template_phase UNIQUE (phase),
    CONSTRAINT chk_dw_ai_prompt_template_phase CHECK (phase IN ('REQUIREMENTS', 'DESIGN', 'GENERATION'))
);

COMMENT ON TABLE dw_ai_prompt_templates IS 'Runtime overrides for the AI generation system prompts; absent row = built-in ai-prompts/<phase>.txt';
COMMENT ON COLUMN dw_ai_prompt_templates.phase IS 'REQUIREMENTS | DESIGN | GENERATION';
COMMENT ON COLUMN dw_ai_prompt_templates.content IS 'Full system prompt text sent to the AI gateway for this phase';
COMMENT ON COLUMN dw_ai_prompt_templates.updated_by IS 'User id of the last editor';
