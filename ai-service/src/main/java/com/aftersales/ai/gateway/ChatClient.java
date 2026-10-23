package com.aftersales.ai.gateway;

/**
 * 对话大模型客户端
 * 设计思路：大模型调用抽象成接口，真实 HTTP 实现与 Mock 实现按配置条件装配，
 * 上层业务（结构化提取/回复生成）不感知底层是哪家模型、是否 Mock
 */
public interface ChatClient {

    /**
     * 自由文本对话
     *
     * @param systemPrompt 系统提示词（角色/约束）
     * @param userPrompt   用户输入
     * @return 模型生成的文本
     */
    String chat(String systemPrompt, String userPrompt);

    /**
     * 结构化输出对话（强制 JSON）
     * 设计思路：用 OpenAI tools/function calling 约束模型只输出 JSON，
     * 比「提示词里要求只输出JSON」稳定得多，解析失败率显著更低
     *
     * @return JSON 字符串
     */
    String chatJson(String systemPrompt, String userPrompt);
}
