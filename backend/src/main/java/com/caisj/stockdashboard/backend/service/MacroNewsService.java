package com.caisj.stockdashboard.backend.service;

import com.caisj.stockdashboard.backend.dto.response.TopicDigestResponse;
import com.caisj.stockdashboard.backend.dto.response.TrumpNewsItemResponse;
import java.util.List;

public interface MacroNewsService {
    List<TrumpNewsItemResponse> getTrumpNews();
    TopicDigestResponse getTopicDigest(String topic);
}
