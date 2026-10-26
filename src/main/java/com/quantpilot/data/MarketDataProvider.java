package com.quantpilot.data;

import com.quantpilot.domain.MarketData;

/**
 * 行情数据源。
 *
 * 为什么不直接写死 akshare：数据源会变（接口改版、限流、收费），
 * 计算层不应该感知这件事。换源只换实现。
 */
public interface MarketDataProvider {

    MarketData load();

    String name();
}
