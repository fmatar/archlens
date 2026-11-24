<script lang="ts">
  interface Props {
    count?: number;
  }

  let { count = 0 }: Props = $props();
</script>

{#if count > 0}
  <div
    role="status"
    data-testid="skipped-files-banner"
    class="px-4 py-1.5 text-xs bg-amber-950/90 border-b border-amber-700/80 text-amber-200 z-40"
  >
    Scan skipped <strong>{count}</strong> unreadable or unparseable source {count === 1 ? 'file' : 'files'}. Check server logs for paths.
  </div>
{/if}
