<script lang="ts">
  interface Props {
    size?: number;
    showSquircle?: boolean;
    pulse?: boolean;
    title?: string;
    class?: string;
  }

  let {
    size = 24,
    showSquircle = true,
    pulse = false,
    title = 'Archlens — Hexagonal Dependency Vault',
    class: className = ''
  }: Props = $props();
</script>

<svg
  xmlns="http://www.w3.org/2000/svg"
  viewBox="0 0 256 256"
  width={size}
  height={size}
  fill="none"
  data-testid="archlens-logo"
  aria-label={title}
  role="img"
  class={`shrink-0 select-none ${pulse ? 'animate-pulse' : ''} ${className}`.trim()}
>
  <title>{title}</title>
  <defs>
    <linearGradient id="archlens-core-gold" x1="102" y1="156" x2="154" y2="104" gradientUnits="userSpaceOnUse">
      <stop offset="0%" stop-color="#F59E0B" />
      <stop offset="100%" stop-color="#FBBF24" />
    </linearGradient>
  </defs>

  {#if showSquircle}
    <rect x="4" y="4" width="248" height="248" rx="56" fill="#0B0F19" stroke="#1E293B" stroke-width="6" />
  {/if}

  <!-- Ring 3: Outer Hexagonal Adapters Vault -->
  <path
    d="M 34.469 184 L 34.469 76 L 128 22 L 221.531 76 L 221.531 184 L 199.014 171 L 199.014 89 L 128 48 L 56.986 89 L 56.986 171 Z"
    fill="#38BDF8"
  />

  <!-- Ring 2: Middle Application Use-Case Vault -->
  <path
    d="M 69.11 164 L 69.11 96 L 128 62 L 186.89 96 L 186.89 164 L 167.837 153 L 167.837 107 L 128 84 L 88.163 107 L 88.163 153 Z"
    fill="#6366F1"
  />

  <!-- Ring 1: Inward Dependency Rule Chevron -->
  <path
    d="M 67.378 205 L 128 170 L 188.622 205 L 177.622 224.053 L 128 195.404 L 78.378 224.053 Z"
    fill="#A855F7"
  />

  <!-- Ring 0: Immutable Domain Core Hexagon -->
  <path
    d="M 128 104 L 150.517 117 L 150.517 143 L 128 156 L 105.483 143 L 105.483 117 Z"
    fill="url(#archlens-core-gold)"
  />
</svg>
