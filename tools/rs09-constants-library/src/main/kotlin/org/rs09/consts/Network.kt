package org.rs09.consts

object Network {
    /** JS5 file server **/
    //Session IDS
    const val ACKNOWLEDGE_ID = 3
    const val STATUS_ID = 0

    //Opcodes
    const val PREFETCH_REQUEST = 0
    const val PRIORITY_REQUEST = 1
    const val STATUS_LOGGED_IN = 2
    const val STATUS_LOGGED_OUT = 3
    const val ACKNOWLEDGE = 6
    const val HANDSHAKE_REQUEST = 15

    //Responses
    const val JS5_GAME_UPDATED = 6
    const val JS5_BAD_SESSION_ID = 10
    const val JS5_REJECT_SESSION = 11
    /** END **/


    /** Account creation **/
    //NETWORK - Receiving opcodes
    const val SUBMIT_ACCOUNT_REGISTRATION = 36
    const val VERIFY_BIRTHDAY_INFORMATION = 147
    const val VERIFY_USERNAME_INFORMATION = 186

    //NETWORK - Reply opcodes
    const val ACCOUNT_CREATION_APPROVAL = 21
    /** End **/

    /** Account creation client response **/
    const val UNEXPECTED_SERVER_RESPONSE = 0
    const val SUCCESS = 2
    const val ERROR_CONTACTING_CREATE_SYSTEM = 3
    const val SERVER_BUSY = 7
    const val CANNOT_CREATE_ACCOUNT_TRY_LATER = 9
    const val INVALID_BIRTHDAY = 10
    const val INVALID_BIRTHDAY_FUTURE = 11
    const val INVALID_BIRTHDAY_THIS_YEAR = 12
    const val INVALID_BIRTHDAY_LAST_YEAR = 13
    const val COUNTRY_ENTERED_INVALID = 14
    /** End **/

    /** Login handler **/
    const val LOGIN_REQUEST = 14
    const val USER_NEW_LOGIN = 16
    const val USER_RECONNECTION_LOGIN = 18
    /** End **/

    /** Error messages | Login **/
    const val CONNECTION_TIMED_OUT = -5
    const val ERROR_CONNECTING_TO_SERVER = -4
    const val PERFORMING_LOGIN = -3
    const val ENTER_USERNAME_AND_PASSWORD = -2
    const val COULD_NOT_DISPLAY_VIDEO_AD = 1
    const val UNEXPECTED_SERVER_RESPONSE_2 = 2
    const val INVALID_USER_OR_PASSWORD = 3
    const val ACCOUNT_DISABLED = 4
    const val ACCOUNT_STILL_LOGGED_IN = 5
    const val GAME_HAS_UPDATED = 6
    const val WORLD_IS_FULL = 7
    const val LOGIN_SERVER_OFFLINE = 8
    const val TOO_MANY_CONNECTIONS = 9
    const val BAD_SESSION_ID = 10
    const val WEAK_PASSWORD_ALERT = 11
    const val NON_MEMBERS_ACCOUNT = 12
    const val COULD_NOT_COMPLETE_LOGIN = 13
    const val SERVER_BEING_UPDATED_WAIT = 14
    const val UNEXPECTED_SERVER_RESPONSE_15 = 15
    const val MAX_INCORRECT_LOGIN_AMOUNT = 16
    const val FREE_ACCOUNT_IN_MEMBERS_AREA = 17
    const val LOCKED_ACCOUNT_STOLEN = 18
    const val FULLSCREEN_MEMBERS_ONLY = 19
    const val MALFORMED_LOGIN_PACKET = 22
    const val NO_LOGIN_SERVER_REPLY = 23
    const val ERROR_LOADING_PROFILE = 24
    const val MAC_BANNED = 26
    const val SERVICE_UNAVAILABLE = 27

    //Special*
    const val REQUIREMENT_WORLD = 29
    //REQUIREMENT WORLD OPTIONS --
    const val REQ_ADD_PVP_COMBAT_MINIMUM_LEVEL_20 = 0
    const val REQ_ADD_PVP_CARRYING_LENT_ITEMS = 1
    //END REQ WORLD OPTIONS --

    const val NON_MEMBER_MEMBER_LOGIN = 30
    /** END **/

    /**
     * Packet master list
     * Group 1: Server to Client
     * Group 2: Client to Server
     */
    //Server to client
    const val UPDATE_SCENE_GRAPH = 162
    const val WINDOW_PANE = 145
    const val SEND_INTERFACE = 155
    const val SEND_SKILL_LEVEL = 38
    const val VARP_SMALL = 60
    const val VARP_LARGE = 226
    const val ACCESS_MASK = 165
    const val SEND_GAME_MESSAGE = 70
    const val RUN_SCRIPT = 115
    const val UPDATE_RUN_ENERGY = 234
    const val UPDATE_CONTAINER = 144
    const val SET_TEXT = 171
    const val LOGOUT_FULL = 86
    const val CLOSE_INTERFACE = 149
    const val ANIMATE_INTERFACE = 36
    const val DISPLAY_MODEL_PLAYER = 66
    const val DISPLAY_MODEL_NPC = 73
    const val DISPLAY_MODEL_ITEM = 50
    const val DISPLAY_MODEL = 130
    const val INTERFACE_SCRIPT = 21
}