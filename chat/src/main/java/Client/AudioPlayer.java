package Client;


import javax.sound.sampled.*;
import java.io.File;
import java.io.IOException;

public class AudioPlayer {
    public static void reproducirAudio(File archivo) {
        try (AudioInputStream audioStream = AudioSystem.getAudioInputStream(archivo)) {
            Clip clip = AudioSystem.getClip();
            clip.open(audioStream);
            clip.start();

            while (clip.isRunning()) {
                Thread.sleep(100);
            }
            clip.close();

        } catch (UnsupportedAudioFileException | IOException | LineUnavailableException | InterruptedException e) {
            e.printStackTrace();
        }
    }
}
