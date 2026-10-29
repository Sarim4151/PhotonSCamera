# Photo grading workspace

The recipe panel's **Grade** mode groups LGGO, existing three-way split toning,
RGB/master curves and the existing perceptual LCH colour mixer. The LCH controls
are labelled as LCH, not HSV/HSL. The gallery editor supplies its rendered preview
bitmap to scopes; there is no extra full-resolution GPU readback for analysis.
The editor's existing original/edited comparison and reset/apply workflow remain
available. Controls change the same recipe used for preview and saved output.

## Working space and LGGO

LGGO operates on the colour pass's existing display-referred RGB values before
three-way toning. It is not an ACES, DaVinci Wide Gamut or camera Log conversion.
Each channel uses zero-centred deltas, with lift, gamma, gain and offset limited
to −0.5 through +0.5 in the UI and uniform binder:

```
lifted = max(c + lift * (1 - c), 0)
result = (lifted * (1 + gain)) ^ (1 / (1 + gamma)) + offset
```

Lift anchors white, gain anchors black, gamma changes midtone curvature and
offset shifts the whole channel. The existing colour pass sanitization/clipping
still applies. A tint wheel adjusts channel differences around the current
channel mean; the master slider shifts that mean, and RGB sliders offer direct
control. Double tapping a wheel resets its three channels. Shared `LggoTransform`
shader code is consumed by preview, still processing and the existing video
recipe consumers; the new workspace is a photo editor feature.

All twelve channel deltas persist in recipe JSON, embedded current/baseline
recipes and renderer snapshots. Database migration 51→52 adds the columns with
zero values for existing non-null recipes; null embedded recipes remain null.
Older JSON defaults to zero, preserving its neutral LGGO identity.

## Scopes

Analysis is cancelled when the preview changes, delayed 140 ms while controls
move, and computed on `Dispatchers.Default`. The sample's longest edge is at most
192 pixels; scope histograms are 64 × 64. Transparent pixels are excluded.
Waveform retains horizontal image position, with 100% at the top and 0% at the
bottom. Density is logarithmically displayed.

Scopes apply Rec.709 luma coefficients to the encoded rendered RGB sample:
`Y′ = 0.2126 R′ + 0.7152 G′ + 0.0722 B′`. The vectorscope plots
`Cb = (B′ − Y′) / 1.8556`, `Cr = (R′ − Y′) / 1.5748`, centred on neutral.
These are display-referred diagnostic scopes, not scene-linear luminance,
HDR nits, video legal-range compliance measurements or RAW clipping warnings.

Numerical tests cover neutral ramps, lift/gain anchors, gamma and offset,
finite output at black, recipe round trips and legacy defaults, waveform
endpoints, Rec.709 primary positions, neutral chroma and transparent pixels.
A Kotlin build or desktop reference test does not validate GLES device driver
execution; preview/export pixel parity and gesture feel require an Android
runtime check.

## Verification performed

The six new JUnit tests passed under cached Kotlin 2.0.21/JVM 17, compiling the
production scope, LGGO reference, recipe model and legacy migration classes
(with only Android `Keep` and generated resource identifiers stubbed for the
standalone JVM run). A SQLite execution of migration 51→52 preserved fully null
recipes and initialized all 24 current/baseline LGGO columns to zero for existing
recipes. Both localized resource files parsed as XML. Android Compose compilation
and GLES device execution are separate checks.
