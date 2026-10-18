package com.astrax.elementer;

import java.util.List;

public record LevelReward(int level, String message, List<String> commands) {
}
