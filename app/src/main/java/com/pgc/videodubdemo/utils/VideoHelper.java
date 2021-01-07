package com.pgc.videodubdemo.utils;

import android.media.AudioFormat;
import android.media.MediaCodec;
import android.media.MediaExtractor;
import android.media.MediaFormat;
import android.os.Handler;
import android.text.TextUtils;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;

/**
 * @author Created by PengGuiChu on 2021/1/6 11:57.
 * @explain  视频提取音频帮助类
 */
public class VideoHelper {
    private static final String WAV=".wav";
    private static final String PCM=".pcm";
    //类型
    private static final String mime = "audio/mp4a-latm";

    private Handler handler;

    public VideoHelper(Handler handler) {
        this.handler = handler;
    }

    /**
     *
     * @param dirPath 保存解码后的文件路径
     * @param decodePath 需要解码的MP4文件路径
     * @param audioName  音频名字
     * @param isWav 是否是wav格式   wav可以播放  pcm无法直接播放(裁剪格式)
     */
    public void initDecodeVideoToAudio(String dirPath,String decodePath, String audioName,boolean isWav) throws IOException {
        if (handler!=null){
            handler.sendEmptyMessage(3);
        }
        String suffixName;
        if (isWav){
            suffixName=WAV;
        }else{
            suffixName=PCM;
        }
        MediaExtractor mediaExtractor=new MediaExtractor();
        //设置资源
        mediaExtractor.setDataSource(decodePath);
        //获取含有音频的MediaFormat
        MediaFormat mediaFormat = createMediaFormat(mediaExtractor);
        if (mediaFormat==null){
            if (handler!=null){
                handler.sendEmptyMessage(0);
            }
            return;
        }
        int KEY_SAMPLE_RATE=mediaFormat.getInteger(MediaFormat.KEY_SAMPLE_RATE);
        int KEY_CHANNEL_COUNT=mediaFormat.getInteger(MediaFormat.KEY_CHANNEL_COUNT);
        int KEY_PCM_ENCODING= AudioFormat.ENCODING_PCM_16BIT;
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.N&&mediaFormat.containsKey(MediaFormat.KEY_PCM_ENCODING)) {
            KEY_PCM_ENCODING=mediaFormat.getInteger(MediaFormat.KEY_PCM_ENCODING);
        }
        int bitNumber;
        switch (KEY_PCM_ENCODING) {
            case AudioFormat.ENCODING_PCM_FLOAT:
                bitNumber = 32;
                break;
            case AudioFormat.ENCODING_PCM_8BIT:
                bitNumber = 8;
                break;
            case AudioFormat.ENCODING_PCM_16BIT:
            default:
                bitNumber = 16;
                break;
        }
        if (handler!=null){
            handler.sendEmptyMessage(1);
        }
        MediaCodec mMediaDecode = MediaCodec.createDecoderByType(mime);
        mMediaDecode.configure(mediaFormat, null, null, 0);//当解压的时候最后一个参数为0
        mMediaDecode.start();//开始，进入runnable状态
        ByteBuffer[] inputBuffers = mMediaDecode.getInputBuffers();
        ByteBuffer[] outputBuffers = mMediaDecode.getOutputBuffers();
        MediaCodec.BufferInfo bufferInfo = new MediaCodec.BufferInfo();
        ByteArrayOutputStream byteArrayOutputStream=new ByteArrayOutputStream();
        decode(mMediaDecode,inputBuffers,mediaExtractor,outputBuffers,bufferInfo,byteArrayOutputStream);
        byte[] bytes=byteArrayOutputStream.toByteArray();
        FileOutputStream fileOutputStream=new FileOutputStream(new File(dirPath,audioName+suffixName));
        if (isWav){
            convertPcmToWav(fileOutputStream,bytes,KEY_SAMPLE_RATE,KEY_CHANNEL_COUNT,bitNumber);
        }else{
            fileOutputStream.write(bytes);
        }
        fileOutputStream.close();
        byteArrayOutputStream.close();
        if (handler!=null){
            handler.sendEmptyMessage(2);
        }
    }


    private void decode(MediaCodec mMediaDecode, ByteBuffer[] inputBuffers, MediaExtractor mMediaExtractor, ByteBuffer[] outputBuffers, MediaCodec.BufferInfo bufferInfo, ByteArrayOutputStream byteArrayOutputStream) {
        boolean inputSawEos = false;
        boolean outputSawEos = false;
        long kTimes = 5000;//循环时间
        while (!outputSawEos) {
            if (!inputSawEos) {
                //每5000毫秒查询一次
                int inputBufferIndex = mMediaDecode.dequeueInputBuffer(kTimes);
                //输入缓存index可用
                if (inputBufferIndex >= 0) {
                    //获取可用的输入缓存
                    ByteBuffer inputBuffer = inputBuffers[inputBufferIndex];
                    //从MediaExtractor读取数据到输入缓存中，返回读取长度
                    int bufferSize = mMediaExtractor.readSampleData(inputBuffer, 0);
                    if (bufferSize <= 0) {//已经读取完
                        //标志输入完毕
                        inputSawEos = true;
                        //做标识
                        mMediaDecode.queueInputBuffer(inputBufferIndex, 0, 0, kTimes, MediaCodec.BUFFER_FLAG_END_OF_STREAM);
                    } else {
                        long time = mMediaExtractor.getSampleTime();
                        //将输入缓存放入MediaCodec中
                        mMediaDecode.queueInputBuffer(inputBufferIndex, 0, bufferSize, time, 0);
                        //指向下一帧
                        mMediaExtractor.advance();
                    }
                }
            }
            //获取输出缓存，需要传入MediaCodec.BufferInfo 用于存储ByteBuffer信息
            int outputBufferIndex = mMediaDecode.dequeueOutputBuffer(bufferInfo, kTimes);
            if (outputBufferIndex >= 0) {
                if ((bufferInfo.flags & MediaCodec.BUFFER_FLAG_CODEC_CONFIG) != 0) {
                    mMediaDecode.releaseOutputBuffer(outputBufferIndex, false);
                    continue;
                }
                //有输出数据
                if (bufferInfo.size > 0) {
                    //获取输出缓存
                    ByteBuffer outputBuffer = outputBuffers[outputBufferIndex];
                    //设置ByteBuffer的position位置
                    outputBuffer.position(bufferInfo.offset);
                    //设置ByteBuffer访问的结点
                    outputBuffer.limit(bufferInfo.offset + bufferInfo.size);
                    byte[] targetData = new byte[bufferInfo.size];
                    //将数据填充到数组中
                    outputBuffer.get(targetData);
                    try {
                        byteArrayOutputStream.write(targetData);
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
                //释放输出缓存
                mMediaDecode.releaseOutputBuffer(outputBufferIndex, false);
                //判断缓存是否完结
                if ((bufferInfo.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                    outputSawEos = true;
                }
            } else if (outputBufferIndex == MediaCodec.INFO_OUTPUT_BUFFERS_CHANGED) {
                outputBuffers = mMediaDecode.getOutputBuffers();
            }
        }
        //释放资源
        mMediaDecode.stop();
        mMediaDecode.release();
        mMediaExtractor.release();
    }


    private MediaFormat createMediaFormat(MediaExtractor mediaExtractor) {
        //获取文件的轨道数，做循环得到含有音频的mediaFormat
        for (int i = 0; i < mediaExtractor.getTrackCount(); i++) {
            MediaFormat mediaFormat = mediaExtractor.getTrackFormat(i);
            //MediaFormat键值对应
            String mime = mediaFormat.getString(MediaFormat.KEY_MIME);
            if (!TextUtils.isEmpty(mime) && mime.contains(VideoHelper.mime)) {
                mediaExtractor.selectTrack(i);
                return mediaFormat;
            }
        }
        return null;
    }

    /**
     * PCM文件转WAV文件
     *
     * @param out            输出WAV文件路径
     * @param sampleRate     采样率，例如15000
     * @param channels       声道数 单声道：1或双声道：2
     * @param bitNum         采样位数，8或16
     * @param totalAudio  字节数组
     */
    private   void convertPcmToWav(FileOutputStream out,
                                 byte[] totalAudio,
                                 int sampleRate,
                                 int channels, int bitNum) {
        try {
            //采样字节byte率
            long byteRate = sampleRate * channels * bitNum / 8;
            //PCM文件大小
            long totalAudioLen = totalAudio.length;
            //总大小，由于不包括RIFF和WAV，所以是44 - 8 = 36，在加上PCM文件大小
            long totalDataLen = totalAudioLen + 36;
            writeWaveFileHeader(out, totalAudioLen, totalDataLen, sampleRate, channels, byteRate);
            out.write(totalAudio, 0, totalAudio.length);
        } catch (Exception e) {
            e.printStackTrace();
            if (handler!=null){
                handler.sendEmptyMessage(0);
            }
        } finally {
            if (out != null) {
                try {
                    out.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
    }

    /**
     * 输出WAV文件
     *
     * @param out           WAV输出文件流
     * @param totalAudioLen 整个音频PCM数据大小
     * @param totalDataLen  整个数据大小
     * @param sampleRate    采样率
     * @param channels      声道数
     * @param byteRate      采样字节byte率
     * @throws IOException
     */
    private void writeWaveFileHeader(FileOutputStream out, long totalAudioLen,
                                     long totalDataLen, int sampleRate, int channels, long byteRate) throws IOException {
        byte[] header = new byte[44];
        header[0] = 'R'; // RIFF
        header[1] = 'I';
        header[2] = 'F';
        header[3] = 'F';
        header[4] = (byte) (totalDataLen & 0xff);//数据大小
        header[5] = (byte) ((totalDataLen >> 8) & 0xff);
        header[6] = (byte) ((totalDataLen >> 16) & 0xff);
        header[7] = (byte) ((totalDataLen >> 24) & 0xff);
        header[8] = 'W';//WAVE
        header[9] = 'A';
        header[10] = 'V';
        header[11] = 'E';
        //FMT Chunk
        header[12] = 'f'; // 'fmt '
        header[13] = 'm';
        header[14] = 't';
        header[15] = ' ';//过渡字节
        //数据大小
        header[16] = 16; // 4 bytes: size of 'fmt ' chunk
        header[17] = 0;
        header[18] = 0;
        header[19] = 0;
        //编码方式 10H为PCM编码格式
        header[20] = 1; // format = 1
        header[21] = 0;
        //通道数
        header[22] = (byte) channels;
        header[23] = 0;
        //采样率，每个通道的播放速度
        header[24] = (byte) (sampleRate & 0xff);
        header[25] = (byte) ((sampleRate >> 8) & 0xff);
        header[26] = (byte) ((sampleRate >> 16) & 0xff);
        header[27] = (byte) ((sampleRate >> 24) & 0xff);
        //音频数据传送速率,采样率*通道数*采样深度/8
        header[28] = (byte) (byteRate & 0xff);
        header[29] = (byte) ((byteRate >> 8) & 0xff);
        header[30] = (byte) ((byteRate >> 16) & 0xff);
        header[31] = (byte) ((byteRate >> 24) & 0xff);
        // 确定系统一次要处理多少个这样字节的数据，确定缓冲区，通道数*采样位数
        header[32] = (byte) (channels * 16 / 8);
        header[33] = 0;
        //每个样本的数据位数
        header[34] = 16;
        header[35] = 0;
        //Data chunk
        header[36] = 'd';//data
        header[37] = 'a';
        header[38] = 't';
        header[39] = 'a';
        header[40] = (byte) (totalAudioLen & 0xff);
        header[41] = (byte) ((totalAudioLen >> 8) & 0xff);
        header[42] = (byte) ((totalAudioLen >> 16) & 0xff);
        header[43] = (byte) ((totalAudioLen >> 24) & 0xff);
        out.write(header, 0, 44);
    }
}
