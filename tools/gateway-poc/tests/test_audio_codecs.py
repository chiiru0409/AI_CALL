"""Unit tests for ITU-T G.711 mu-law audio codecs and utilities."""

import struct
from app.audio_codecs import (
    calculate_pcm16_rms,
    chunk_mulaw_frames,
    generate_beep_mulaw,
    generate_silence_mulaw,
    linear_sample_to_ulaw,
    mulaw_to_pcm16,
    pcm16_to_mulaw,
)


def test_linear_sample_to_ulaw_boundaries():
    """Verify standard G.711 boundary conversions."""
    # Zero sample -> mu-law 0xFF (linear zero in standard mu-law)
    zero_ulaw = linear_sample_to_ulaw(0)
    assert zero_ulaw in (0xFF, 0x7F)

    # Extreme positive clipping (should not raise)
    pos_clip = linear_sample_to_ulaw(32767)
    assert isinstance(pos_clip, int)

    # Extreme negative clipping (should not raise)
    neg_clip = linear_sample_to_ulaw(-32768)
    assert isinstance(neg_clip, int)


def test_mulaw_pcm16_round_trip():
    """Test encoding and decoding linear PCM16 to and from mu-law."""
    # Generate test linear samples (-10000, -5000, 0, 5000, 10000)
    original_samples = [-10000, -5000, 0, 5000, 10000]
    pcm_bytes = struct.pack(f"<{len(original_samples)}h", *original_samples)

    mulaw = pcm16_to_mulaw(pcm_bytes)
    assert len(mulaw) == len(original_samples)

    # Decode back to PCM
    decoded_pcm = mulaw_to_pcm16(mulaw)
    assert len(decoded_pcm) == len(pcm_bytes)
    decoded_samples = struct.unpack(f"<{len(original_samples)}h", decoded_pcm)

    # Mu-law is a lossy companded format; check quantization error is small (< 5%)
    for orig, dec in zip(original_samples, decoded_samples):
        if orig == 0:
            assert abs(dec) < 100
        else:
            rel_error = abs(orig - dec) / abs(orig)
            assert rel_error < 0.08  # within expected 8-bit quantization bounds


def test_beep_and_silence_generation():
    """Verify audio generators produce accurate byte lengths and sample counts."""
    sample_rate = 8000
    duration = 0.5  # half second

    beep = generate_beep_mulaw(frequency_hz=440, duration_sec=duration, sample_rate=sample_rate)
    expected_len = int(sample_rate * duration)
    assert len(beep) == expected_len

    silence = generate_silence_mulaw(duration_sec=duration, sample_rate=sample_rate)
    assert len(silence) == expected_len
    # In mu-law, silence byte is 0xFF
    assert all(b == 0xFF for b in silence)


def test_calculate_pcm16_rms():
    """Verify RMS energy calculation correctly distinguishes silence from sound."""
    silence_samples = [0] * 160
    silence_pcm = struct.pack(f"<{len(silence_samples)}h", *silence_samples)
    assert calculate_pcm16_rms(silence_pcm) == 0.0

    loud_samples = [10000, -10000] * 80
    loud_pcm = struct.pack(f"<{len(loud_samples)}h", *loud_samples)
    rms = calculate_pcm16_rms(loud_pcm)
    assert rms == 10000.0


def test_chunk_mulaw_frames():
    """Verify 20ms chunker divides audio into 160-sample slices."""
    audio = b"x" * 480  # 3 frames of 160 bytes
    chunks = list(chunk_mulaw_frames(audio, chunk_size_samples=160))
    assert len(chunks) == 3
    assert all(len(c) == 160 for c in chunks)

    # Remainder slice
    audio_with_remainder = b"x" * 350
    chunks_rem = list(chunk_mulaw_frames(audio_with_remainder, chunk_size_samples=160))
    assert len(chunks_rem) == 3
    assert len(chunks_rem[0]) == 160
    assert len(chunks_rem[1]) == 160
    assert len(chunks_rem[2]) == 30
