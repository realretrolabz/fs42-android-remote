package com.realretrolabz.fs42remote

val DefaultRemoteDisplayRect = DefaultRemoteRect(
    x = 0.264f,
    y = 0.837f,
    w = 0.470f,
    h = 0.099f,
)

val DefaultRemoteZones = listOf(
    DefaultRemoteZone("POWER", RemoteCommand.POWER_STOP, 0.265f, 0.063f, 0.128f, 0.061f),
    DefaultRemoteZone("GUIDE", RemoteCommand.GUIDE, 0.423f, 0.128f, 0.159f, 0.067f),
    DefaultRemoteZone("VOL+", RemoteCommand.VOLUME_UP, 0.264f, 0.194f, 0.135f, 0.087f),
    DefaultRemoteZone("VOL-", RemoteCommand.VOLUME_DOWN, 0.264f, 0.287f, 0.135f, 0.078f),
    DefaultRemoteZone("CH+", RemoteCommand.CHANNEL_UP, 0.602f, 0.194f, 0.133f, 0.087f),
    DefaultRemoteZone("CH-", RemoteCommand.CHANNEL_DOWN, 0.602f, 0.287f, 0.133f, 0.078f),
    DefaultRemoteZone("1", RemoteCommand.DIGIT_1, 0.260f, 0.408f, 0.146f, 0.066f),
    DefaultRemoteZone("2", RemoteCommand.DIGIT_2, 0.424f, 0.408f, 0.149f, 0.066f),
    DefaultRemoteZone("3", RemoteCommand.DIGIT_3, 0.592f, 0.408f, 0.148f, 0.066f),
    DefaultRemoteZone("4", RemoteCommand.DIGIT_4, 0.260f, 0.479f, 0.146f, 0.066f),
    DefaultRemoteZone("5", RemoteCommand.DIGIT_5, 0.424f, 0.479f, 0.149f, 0.066f),
    DefaultRemoteZone("6", RemoteCommand.DIGIT_6, 0.592f, 0.479f, 0.148f, 0.066f),
    DefaultRemoteZone("7", RemoteCommand.DIGIT_7, 0.260f, 0.550f, 0.146f, 0.065f),
    DefaultRemoteZone("8", RemoteCommand.DIGIT_8, 0.424f, 0.550f, 0.149f, 0.065f),
    DefaultRemoteZone("9", RemoteCommand.DIGIT_9, 0.592f, 0.550f, 0.148f, 0.065f),
    DefaultRemoteZone("MUTE", RemoteCommand.MUTE, 0.260f, 0.621f, 0.146f, 0.066f),
    DefaultRemoteZone("0", RemoteCommand.DIGIT_0, 0.424f, 0.621f, 0.149f, 0.066f),
    DefaultRemoteZone("LAST", RemoteCommand.LAST_CHANNEL, 0.592f, 0.621f, 0.148f, 0.066f),
    DefaultRemoteZone("PPV PAGE LEFT", RemoteCommand.PPV_PAGE_PREV, 0.234f, 0.733f, 0.123f, 0.064f),
    DefaultRemoteZone("PPV", RemoteCommand.PPV_MENU, 0.368f, 0.734f, 0.126f, 0.064f),
    DefaultRemoteZone("PPV SELECT", RemoteCommand.PPV_SELECT, 0.506f, 0.735f, 0.133f, 0.064f),
    DefaultRemoteZone("PPV PAGE RIGHT", RemoteCommand.PPV_PAGE_NEXT, 0.648f, 0.733f, 0.121f, 0.064f),
)
