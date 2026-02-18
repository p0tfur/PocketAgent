import { redirect } from '@sveltejs/kit';
import { db } from '$lib/server/db';
import { user as userTable } from '$lib/server/db/schema';
import { eq } from 'drizzle-orm';
import type { LayoutServerLoad } from './$types';

export const load: LayoutServerLoad = async ({ locals, url }) => {
	if (!locals.user) {
		redirect(307, `/login?redirect=${encodeURIComponent(url.pathname)}`);
	}

	// Check plan status (skip for the activate page itself)
	if (!url.pathname.startsWith('/dashboard/activate')) {
		const rows = await db
			.select({ plan: userTable.plan, polarLicenseKey: userTable.polarLicenseKey })
			.from(userTable)
			.where(eq(userTable.id, locals.user.id))
			.limit(1);

		if (!rows[0]?.plan) {
			redirect(307, '/dashboard/activate');
		}

		return {
			user: locals.user,
			sessionToken: locals.session?.token ?? '',
			plan: rows[0].plan,
			licenseKey: rows[0].polarLicenseKey
		};
	}

	return {
		user: locals.user,
		sessionToken: locals.session?.token ?? '',
		plan: null,
		licenseKey: null
	};
};
