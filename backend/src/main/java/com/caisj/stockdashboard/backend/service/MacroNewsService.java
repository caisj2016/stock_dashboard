package com.caisj.stockdashboard.backend.service;

import com.caisj.stockdashboard.backend.dto.response.TopicDigestResponse;
import com.caisj.stockdashboard.backend.dto.response.TrumpNewsItemResponse;
import java.util.List;

/**
 * 提供宏观新闻与主题摘要能力。
 * 负责聚合政治与行业热点资讯，支持 Trump 新闻和主题驱动摘要。
 */
public interface MacroNewsService {
    /**
     * 获取特朗普相关的宏观新闻列表。
     */
    List<TrumpNewsItemResponse> getTrumpNews();

    /**
     * 获取指定主题的新闻摘要与驱动因子。
     */
    TopicDigestResponse getTopicDigest(String topic);
}
