package com.example.maoshahin.finalsender;

/**
 * Created by mao on 15/11/30.
 */

import android.util.Base64;

import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

import java.io.IOException;

public class Frame {

    public String TYPE;
    public int SEQ;
    public byte[] DATA;

    public Frame (String t, int seq, byte[] data){
        TYPE = t;
        SEQ = seq;
        DATA = data;
    }

    public static Frame createFrameFromString(String arrived) throws IOException {
        ObjectMapper mapper = new ObjectMapper();
        Frame f = mapper.readValue(arrived, Frame.class);
        return f;
    }

    public byte[] toByte() throws JsonProcessingException {
        ObjectMapper mapper = new ObjectMapper();
        mapper.enable(SerializationFeature.INDENT_OUTPUT);
        String json = mapper.writeValueAsString(this);
        return  Base64.decode(json,Base64.DEFAULT);
    }
}