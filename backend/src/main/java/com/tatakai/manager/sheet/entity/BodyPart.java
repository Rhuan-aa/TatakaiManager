package com.tatakai.manager.sheet.entity;

/**
 * Partes do boneco de ferimentos. Os 10 valores originais continuam válidos; desde o §53.3 ARM é o braço (ombro ao
 * cotovelo), LEG a perna (joelho ao tornozelo) e TORSO o tórax. Dedos: 1 = polegar/dedão … 5 = mínimo.
 */
public enum BodyPart {
    HEAD, TORSO, LEFT_ARM, RIGHT_ARM, LEFT_HAND, RIGHT_HAND, LEFT_LEG, RIGHT_LEG, LEFT_FOOT, RIGHT_FOOT,
    NECK, ABDOMEN, PELVIS,
    LEFT_SHOULDER, RIGHT_SHOULDER, LEFT_ELBOW, RIGHT_ELBOW, LEFT_FOREARM, RIGHT_FOREARM,
    LEFT_THIGH, RIGHT_THIGH, LEFT_KNEE, RIGHT_KNEE,
    LEFT_FINGER_1, LEFT_FINGER_2, LEFT_FINGER_3, LEFT_FINGER_4, LEFT_FINGER_5,
    RIGHT_FINGER_1, RIGHT_FINGER_2, RIGHT_FINGER_3, RIGHT_FINGER_4, RIGHT_FINGER_5,
    LEFT_TOE_1, LEFT_TOE_2, LEFT_TOE_3, LEFT_TOE_4, LEFT_TOE_5,
    RIGHT_TOE_1, RIGHT_TOE_2, RIGHT_TOE_3, RIGHT_TOE_4, RIGHT_TOE_5
}
