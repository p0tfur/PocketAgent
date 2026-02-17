<script lang="ts">
	import { listDevices } from '$lib/api/devices.remote';
	import { dashboardWs } from '$lib/stores/dashboard-ws.svelte';
	import { onMount } from 'svelte';
	import DeviceCard from '$lib/components/DeviceCard.svelte';

	const initialDevices = await listDevices();

	let devices = $state(
		initialDevices.map((d) => ({
			deviceId: d.deviceId,
			name: d.name,
			status: d.status as 'online' | 'offline',
			model: d.model,
			manufacturer: d.manufacturer,
			androidVersion: d.androidVersion,
			screenWidth: d.screenWidth,
			screenHeight: d.screenHeight,
			batteryLevel: d.batteryLevel,
			isCharging: d.isCharging,
			lastSeen: d.lastSeen,
			lastGoal: d.lastGoal
		}))
	);

	onMount(() => {
		const unsub = dashboardWs.subscribe((msg) => {
			if (msg.type === 'device_online') {
				const id = msg.deviceId as string;
				const name = msg.name as string;
				const existing = devices.find((d) => d.deviceId === id);
				if (existing) {
					existing.status = 'online';
					existing.lastSeen = new Date().toISOString();
					devices = [...devices];
				} else {
					devices = [
						{
							deviceId: id,
							name,
							status: 'online',
							model: null,
							manufacturer: null,
							androidVersion: null,
							screenWidth: null,
							screenHeight: null,
							batteryLevel: null,
							isCharging: false,
							lastSeen: new Date().toISOString(),
							lastGoal: null
						},
						...devices
					];
				}
			} else if (msg.type === 'device_offline') {
				const id = msg.deviceId as string;
				const existing = devices.find((d) => d.deviceId === id);
				if (existing) {
					existing.status = 'offline';
					devices = [...devices];
				}
			} else if (msg.type === 'device_status') {
				const id = msg.deviceId as string;
				const existing = devices.find((d) => d.deviceId === id);
				if (existing) {
					existing.batteryLevel = msg.batteryLevel as number;
					existing.isCharging = msg.isCharging as boolean;
					devices = [...devices];
				}
			}
		});
		return unsub;
	});
</script>

<h2 class="mb-6 text-2xl font-bold">Devices</h2>

{#if devices.length === 0}
	<div class="rounded-lg border border-neutral-200 p-8 text-center">
		<p class="text-neutral-500">No devices connected.</p>
		<p class="mt-2 text-sm text-neutral-400">
			Install the Android app, paste your API key, and your device will appear here.
		</p>
		<a href="/dashboard/api-keys" class="mt-4 inline-block text-sm text-blue-600 hover:underline">
			Create an API key
		</a>
	</div>
{:else}
	<div class="grid grid-cols-2 gap-6 sm:grid-cols-3 lg:grid-cols-4 xl:grid-cols-5">
		{#each devices as d (d.deviceId)}
			<DeviceCard {...d} />
		{/each}
	</div>
{/if}
