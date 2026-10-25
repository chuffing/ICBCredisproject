package com.danuvius.platform.service;

import com.danuvius.platform.model.dto.ServerSwitchRequestDTO;
import com.danuvius.platform.model.vo.ServerSwitchResponseVO;

/**
 * Redis服务器切换服务。
 */
public interface ServerSwitchService {

    ServerSwitchResponseVO switchServer(ServerSwitchRequestDTO request);

    ServerSwitchResponseVO getSwitchStatus(String taskNo);
}
