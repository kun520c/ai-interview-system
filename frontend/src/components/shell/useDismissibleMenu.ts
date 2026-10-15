import { nextTick, onUnmounted, ref, watch, type Ref } from 'vue'

const FOCUSABLE =
  'a[href], button:not([disabled]), textarea:not([disabled]), input:not([disabled]), select:not([disabled])'

function resolveElement(value: unknown): HTMLElement | null {
  if (value instanceof HTMLElement) {
    return value
  }
  if (value && typeof value === 'object' && '$el' in value) {
    const el = (value as { $el: unknown }).$el
    return el instanceof HTMLElement ? el : null
  }
  return null
}

export function useDismissibleMenu(
  triggerRef: Ref<unknown>,
  panelRef: Ref<HTMLElement | null>,
) {
  const open = ref(false)
  let previousOverflow = ''

  function focusableItems(): HTMLElement[] {
    if (!panelRef.value) {
      return []
    }
    return [...panelRef.value.querySelectorAll<HTMLElement>(FOCUSABLE)]
  }

  function close(): void {
    open.value = false
  }

  function toggle(): void {
    open.value = !open.value
  }

  function onKeydown(event: KeyboardEvent): void {
    if (!open.value) {
      return
    }
    if (event.key === 'Escape') {
      event.preventDefault()
      close()
      return
    }
    if (event.key !== 'Tab') {
      return
    }
    const items = focusableItems()
    if (items.length === 0) {
      return
    }
    const first = items[0]
    const last = items[items.length - 1]
    const active = document.activeElement
    if (event.shiftKey && active === first) {
      event.preventDefault()
      last.focus()
    } else if (!event.shiftKey && active === last) {
      event.preventDefault()
      first.focus()
    }
  }

  watch(open, async (isOpen, wasOpen) => {
    if (isOpen) {
      previousOverflow = document.body.style.overflow
      document.body.style.overflow = 'hidden'
      document.addEventListener('keydown', onKeydown)
      await nextTick()
      focusableItems()[0]?.focus()
      return
    }
    document.removeEventListener('keydown', onKeydown)
    document.body.style.overflow = previousOverflow
    if (wasOpen) {
      resolveElement(triggerRef.value)?.focus()
    }
  })

  onUnmounted(() => {
    document.removeEventListener('keydown', onKeydown)
    document.body.style.overflow = previousOverflow
  })

  return {
    open,
    close,
    toggle,
  }
}
