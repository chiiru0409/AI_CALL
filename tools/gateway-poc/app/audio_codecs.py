"""ITU-T G.711 mu-law (PCMU) Audio Codec and Audio Utilities.

Twilio Media Streams strictly use 8000Hz, mono, 8-bit G.711 mu-law audio
(RFC 3551 / PCMU). Inbound and outbound frames are transferred in Base64.
"""

import math
import struct
from typing import Generator, List, Tuple


# ITU-T G.711 mu-law decompression lookup table (8-bit mu-law -> 16-bit signed PCM)
_ULAW_TO_LINEAR_TABLE: List[int] = []
for _i in range(256):
    _u = ~_i & 0xFF
    _sign = _u & 0x80
    _exponent = (_u >> 4) & 0x07
    _mantissa = _u & 0x0F
    _linear = ((_mantissa << 3) + 0x84) << _exponent
    _linear -= 0x84
    _sample = -_linear if _sign != 0 else _linear
    _ULAW_TO_LINEAR_TABLE.append(_sample)


# ITU-T G.711 mu-law compression boundaries
_BIAS = 0x84
_CLIP = 32635
_SEG_END = [0x3F, 0x7F, 0xFF, 0x1FF, 0x3FF, 0x7FF, 0xFFF, 0x1FFF]


try:
    import audioop
except ImportError:
    import audioop_lts as audioop  # Drop-in replacement for Python 3.13+


def mulaw_to_pcm16(mulaw_bytes: bytes) -> bytes:
    """Decode raw G.711 mu-law byte string to 16-bit signed linear PCM bytes (8000Hz)."""
    return audioop.ulaw2lin(mulaw_bytes, 2)


def pcm16_to_mulaw(pcm16_bytes: bytes) -> bytes:
    """Encode 16-bit signed linear PCM bytes (8000Hz mono) to G.711 mu-law bytes."""
    return audioop.lin2ulaw(pcm16_bytes, 2)


def linear_sample_to_ulaw(sample: int) -> int:
    """Convert a single 16-bit signed PCM sample to 8-bit mu-law."""
    pcm = struct.pack("<h", max(-32768, min(32767, sample)))
    return audioop.lin2ulaw(pcm, 2)[0]


def calculate_pcm16_rms(pcm16_bytes: bytes) -> float:
    """Calculate Root Mean Square (RMS) energy level of 16-bit PCM for Voice Activity Detection."""
    num_samples = len(pcm16_bytes) // 2
    if num_samples == 0:
        return 0.0
    samples = struct.unpack(f"<{num_samples}h", pcm16_bytes[:num_samples * 2])
    sum_squares = sum(s * s for s in samples)
    return math.sqrt(sum_squares / num_samples)


def generate_beep_mulaw(frequency_hz: int = 440, duration_sec: float = 0.5, sample_rate: int = 8000) -> bytes:
    """Generate a clean sinusoidal tone encoded in G.711 mu-law at 8000Hz."""
    total_samples = int(duration_sec * sample_rate)
    pcm_out = []
    amplitude = 16000  # half scale to prevent clipping
    for i in range(total_samples):
        val = int(amplitude * math.sin(2.0 * math.pi * frequency_hz * i / sample_rate))
        pcm_out.append(val)
    raw_pcm = struct.pack(f"<{total_samples}h", *pcm_out)
    return pcm16_to_mulaw(raw_pcm)


def generate_silence_mulaw(duration_sec: float = 0.5, sample_rate: int = 8000) -> bytes:
    """Generate G.711 mu-law silence. In mu-law, 0xFF represents linear zero."""
    total_samples = int(duration_sec * sample_rate)
    return b"\xff" * total_samples


def chunk_mulaw_frames(
    mulaw_bytes: bytes,
    chunk_size_samples: int = 160
) -> Generator[bytes, None, None]:
    """Chunk mu-law audio into frames for Twilio streaming.

    At 8000Hz, 160 samples = exactly 20ms of audio, which is the standard
    Twilio Media Streams chunk size.
    """
    for i in range(0, len(mulaw_bytes), chunk_size_samples):
        yield mulaw_bytes[i:i + chunk_size_samples]
