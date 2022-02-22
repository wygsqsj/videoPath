//
// Created by DELL on 2022/2/20.
//

#ifndef VIDEOPATH_AUDIOPLAYSTATUS_H
#define VIDEOPATH_AUDIOPLAYSTATUS_H


class AudioPlayStatus {

public:
    bool exit = false;
    bool isSeek = false;

public:

    AudioPlayStatus();

    bool GStatus();
};


#endif //VIDEOPATH_AUDIOPLAYSTATUS_H
