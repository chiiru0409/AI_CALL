import os
import time
import pythoncom
import win32com.client

class SpeechRecognitionHandler:
    def __init__(self):
        self.transcripts = []
        self.stream_ended = False

    def OnRecognition(self, *args):
        try:
            result = args[3] if len(args) > 3 else args[-1]
            phrase = win32com.client.Dispatch(result).PhraseInfo.GetText()
            print(f"  [SAPI Event] Recognized: '{phrase}'")
            self.transcripts.append(phrase)
        except Exception as e:
            print(f"  [SAPI Event] PhraseInfo error: {e}")

    def OnHypothesis(self, *args):
        try:
            result = args[3] if len(args) > 3 else args[-1]
            phrase = win32com.client.Dispatch(result).PhraseInfo.GetText()
            print(f"  [SAPI Event] Hypothesis: '{phrase}'")
        except Exception:
            pass

    def OnFalseRecognition(self, *args):
        print("  [SAPI Event] False recognition / no match")

    def OnEndStream(self, *args):
        print(f"  [SAPI Event] Stream ended")
        self.stream_ended = True

def transcribe_wav_with_sapi(wav_file: str):
    file_size = os.path.getsize(wav_file)
    print(f"Testing SAPI ASR on: {wav_file} (Size: {file_size} bytes)")
    t0 = time.time()

    reco = win32com.client.Dispatch("SAPI.SpInprocRecognizer")
    cat = win32com.client.Dispatch("SAPI.SpObjectTokenCategory")
    cat.SetId(r"HKEY_LOCAL_MACHINE\SOFTWARE\Microsoft\Speech\Recognizers", False)
    for token in cat.EnumerateTokens():
        if "English - US" in token.GetDescription():
            reco.Recognizer = token
            print("Selected Recognizer:", token.GetDescription())
            break

    file_stream = win32com.client.Dispatch("SAPI.SpFileStream")
    # SSFMOpenForRead = 0 (DO NOT use 3 which is SSFMCreateForWrite!)
    file_stream.Open(wav_file, 0)
    reco.AudioInputStream = file_stream

    context = reco.CreateRecoContext()
    context.EventInterests = 393215 # SREAllEvents
    event_context = win32com.client.WithEvents(context, SpeechRecognitionHandler)
    
    grammar = context.CreateGrammar()
    grammar.DictationLoad("", 0)
    grammar.DictationSetState(1)

    start_wait = time.time()
    while not event_context.stream_ended and (time.time() - start_wait < 10.0):
        pythoncom.PumpWaitingMessages()
        time.sleep(0.05)

    duration = time.time() - t0
    final_text = " ".join(event_context.transcripts).strip()
    print(f"SAPI ASR Processing Finished in {duration:.3f}s")
    print(f"Transcribed Text: '{final_text}'")
    return final_text, duration

if __name__ == "__main__":
    test_wav = r"C:\Users\chiru\Downloads\EqualAI_Analysis\Phase8_Local_LAN_Prototype\test_tts_David.wav"
    transcribe_wav_with_sapi(test_wav)
